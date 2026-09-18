package com.olivia.companion.session

import android.graphics.ImageFormat
import android.media.Image
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
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
    private val thread = HandlerThread("OliviaHevcDecoder").apply { start() }
    private val handler = Handler(thread.looper)
    private val imageReader = ImageReader.newInstance(width, height, ImageFormat.YUV_420_888, MAX_IMAGES)
    private val codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_HEVC)
    private var cachedConfig: ByteArray? = null
    private var active = false
    private var closed = false

    init {
        imageReader.setOnImageAvailableListener({ reader ->
            reader.acquireLatestImage()?.use { image ->
                if (!closed) runCatching { onFrame(image.width, image.height, image.toNv21()) }
                    .onFailure { onUnavailable() }
            }
        }, handler)
    }

    fun enqueue(buffer: ByteBuffer, presentationTimeUs: Long, isCodecConfig: Boolean) {
        if (closed) return
        val packet = buffer.copyRemaining()
        if (packet.isEmpty()) return
        runCatching {
            if (isCodecConfig || packet.hasConfigNal()) {
                cachedConfig = packet
                if (active) queue(packet, 0L, MediaCodec.BUFFER_FLAG_CODEC_CONFIG)
                return
            }
            if (!active) {
                if (!packet.hasIrapNal()) return
                activate()
                cachedConfig?.let { queue(it, 0L, MediaCodec.BUFFER_FLAG_CODEC_CONFIG) }
                active = true
            }
            queue(
                packet,
                presentationTimeUs,
                if (packet.hasIrapNal()) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0,
            )
            drain()
        }.onFailure {
            active = false
            onUnavailable()
        }
    }

    private fun activate() {
        codec.configure(
            MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, width, height),
            imageReader.surface,
            null,
            0,
        )
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
                else -> if (index >= 0) codec.releaseOutputBuffer(index, true)
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        if (active) runCatching { codec.stop() }
        runCatching { codec.release() }
        imageReader.close()
        thread.quitSafely()
    }

    private fun ByteBuffer.copyRemaining(): ByteArray = duplicate().let { source ->
        ByteArray(source.remaining()).also(source::get)
    }

    private fun ByteArray.hasConfigNal(): Boolean = nalTypes().any { it in 32..34 }

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
        val output = ByteArray(width * height * 3 / 2)
        val y = planes[0]
        val u = planes[1]
        val v = planes[2]
        copyPlane(y, width, height, output, 0)
        var offset = width * height
        for (row in 0 until height / 2) for (column in 0 until width / 2) {
            output[offset++] = v.byteAt(row, column)
            output[offset++] = u.byteAt(row, column)
        }
        return output
    }

    private fun copyPlane(plane: Image.Plane, columns: Int, rows: Int, output: ByteArray, start: Int) {
        var offset = start
        for (row in 0 until rows) for (column in 0 until columns) output[offset++] = plane.byteAt(row, column)
    }

    private fun Image.Plane.byteAt(row: Int, column: Int): Byte =
        buffer.get(row * rowStride + column * pixelStride)

    private companion object {
        const val MAX_IMAGES = 2
        const val INPUT_TIMEOUT_US = 10_000L
        const val OUTPUT_TIMEOUT_US = 0L
    }
}
