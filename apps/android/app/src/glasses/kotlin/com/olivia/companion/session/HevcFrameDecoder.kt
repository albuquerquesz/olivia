package com.olivia.companion.session

import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import java.nio.ByteBuffer

/**
 * Local-only HEVC decoder for DAT's compressed camera stream.
 *
 * DAT can deliver codec-config packets before the first independently decodable image.  The
 * decoder therefore caches config and only starts accepting picture packets at an IRAP frame;
 * this prevents emitting stale/garbled buffers while a mock or glasses stream comes online.
 * Output is an ephemeral NV21 frame and neither input nor output is written to disk.
 */
internal class HevcFrameDecoder(
    private val width: Int,
    private val height: Int,
    private val onFrame: (width: Int, height: Int, nv21: ByteArray) -> Unit,
    private val onUnavailable: () -> Unit,
) : AutoCloseable {
    private val codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_HEVC)
    private var cachedConfig: ByteArray? = null
    private var active = false
    private var closed = false

    @Synchronized
    fun enqueue(buffer: ByteBuffer, presentationTimeUs: Long, isCodecConfig: Boolean) {
        if (closed) return
        val packet = buffer.copyRemaining()
        if (packet.isEmpty()) return
        runCatching {
            val config = packet.configNals()
            if (config.isNotEmpty()) {
                cachedConfig = ((cachedConfig ?: ByteArray(0)) + config).distinctConfigNals()
            }
            // Glasses can put VPS/SPS/PPS and the first keyframe in one access unit.
            // It is a picture packet as well: never discard it merely for containing CSD.
            if (!packet.hasPictureNal()) {
                return
            }
            if (!active) {
                if (!packet.hasIrapNal()) return
                activate()
                // csd-0 is submitted automatically by MediaCodec.start(). Sending a second
                // codec-config buffer here violates that contract and can disrupt startup.
                active = true
            }
            queue(
                packet,
                presentationTimeUs,
                if (packet.hasIrapNal()) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0,
            )
            drain()
        }.onFailure {
            Log.w("OliviaHevcDecoder", "decode_failed type=${it.javaClass.simpleName}")
            onUnavailable()
        }
    }

    private fun activate() {
        // Buffer output avoids the Moto G60's native ImageReader SurfaceImage plane crash.
        // Flexible YUV exposes CPU-readable planes directly from MediaCodec, without a Surface.
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, width, height)
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
        cachedConfig?.let { format.setByteBuffer("csd-0", ByteBuffer.wrap(it)) }
        codec.configure(format, null, null, 0)
        codec.start()
    }

    private fun queue(packet: ByteArray, presentationTimeUs: Long, flags: Int) {
        val index = codec.dequeueInputBuffer(INPUT_TIMEOUT_US)
        if (index < 0) return
        val input = requireNotNull(codec.getInputBuffer(index))
        input.clear()
        input.put(packet)
        codec.queueInputBuffer(index, 0, packet.size, presentationTimeUs, flags)
    }

    private fun drain() {
        val info = MediaCodec.BufferInfo()
        while (true) {
            when (val index = codec.dequeueOutputBuffer(info, OUTPUT_TIMEOUT_US)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> return
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                else -> if (index >= 0) {
                    try {
                        codec.getOutputImage(index)?.use { image ->
                            val crop = image.cropRect
                            onFrame(crop.width(), crop.height(), image.toNv21())
                        }
                    } finally {
                        codec.releaseOutputBuffer(index, false)
                    }
                }
            }
        }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        if (active) runCatching { codec.stop() }
        runCatching { codec.release() }
        cachedConfig?.fill(0)
        cachedConfig = null
    }

    private fun ByteBuffer.copyRemaining(): ByteArray = duplicate().let { source ->
        ByteArray(source.remaining()).also(source::get)
    }

    private fun ByteArray.configNals(): ByteArray = nalUnits()
        .filter { nalType(it) in 32..34 }
        .fold(ByteArray(0)) { result, nal -> result + nal }

    private fun ByteArray.distinctConfigNals(): ByteArray = nalUnits()
        .filter { nalType(it) in 32..34 }
        .associateBy { nalType(it) }.toSortedMap().values
        .fold(ByteArray(0)) { result, nal -> result + nal }

    private fun nalType(nal: ByteArray): Int =
        ((nal[if (nal[2] == 1.toByte()) 3 else 4].toInt() and 0x7e) shr 1)

    private fun ByteArray.nalUnits(): List<ByteArray> {
        val starts = mutableListOf<Int>()
        var i = 0
        while (i + 3 < size) {
            val prefix = if (this[i] == 0.toByte() && this[i + 1] == 0.toByte()) {
                when {
                    this[i + 2] == 1.toByte() -> 3
                    i + 4 < size && this[i + 2] == 0.toByte() && this[i + 3] == 1.toByte() -> 4
                    else -> 0
                }
            } else 0
            if (prefix > 0) { starts += i; i += prefix } else i++
        }
        return starts.mapIndexed { index, start -> copyOfRange(start, starts.getOrElse(index + 1) { size }) }
    }

    private fun ByteArray.hasPictureNal(): Boolean = nalTypes().any { it in 0..31 }

    private fun ByteArray.hasIrapNal(): Boolean = nalTypes().any { it in 16..21 }

    private fun ByteArray.nalTypes(): Sequence<Int> = sequence {
        var index = 0
        while (index + 4 < size) {
            val prefix = when {
                this@nalTypes[index] == 0.toByte() && this@nalTypes[index + 1] == 0.toByte() &&
                    this@nalTypes[index + 2] == 1.toByte() -> 3
                this@nalTypes[index] == 0.toByte() && this@nalTypes[index + 1] == 0.toByte() &&
                    this@nalTypes[index + 2] == 0.toByte() && this@nalTypes[index + 3] == 1.toByte() -> 4
                else -> 0
            }
            if (prefix == 0) {
                index++
            } else {
                yield((this@nalTypes[index + prefix].toInt() and 0x7e) shr 1)
                index += prefix + 1
            }
        }
    }

    private fun Image.toNv21(): ByteArray {
        val crop = cropRect
        val width = crop.width()
        val height = crop.height()
        val output = ByteArray(width * height * 3 / 2)
        val y = planes[0]
        val u = planes[1]
        val v = planes[2]
        var yOffset = 0
        for (row in 0 until height) for (column in 0 until width) {
            output[yOffset++] = y.byteAt(row + crop.top, column + crop.left)
        }
        var offset = width * height
        for (row in 0 until height / 2) for (column in 0 until width / 2) {
            output[offset++] = v.byteAt(row + crop.top / 2, column + crop.left / 2)
            output[offset++] = u.byteAt(row + crop.top / 2, column + crop.left / 2)
        }
        return output
    }

    private fun Image.Plane.byteAt(row: Int, column: Int): Byte =
        buffer.get(buffer.position() + row * rowStride + column * pixelStride)

    private companion object {
        const val INPUT_TIMEOUT_US = 10_000L
        const val OUTPUT_TIMEOUT_US = 0L
    }
}
