package com.olivia.companion.session

import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.olivia.companion.BuildConfig
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
    private val onFrame: (width: Int, height: Int, nv21: ByteArray, presentationTimeUs: Long) -> Unit,
    private val onUnavailable: () -> Unit,
) : AutoCloseable {
    private val codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_HEVC)
    private var cachedConfig: ByteArray? = null
    private var active = false
    private var closed = false
    private val queuedAtNanos = LinkedHashMap<Long, Long>()
    private var outputCount = 0L
    private val outputThread = HandlerThread("OliviaHevcOutput").apply { start() }
    private val outputHandler = Handler(outputThread.looper)
    private val outputDrain = object : Runnable {
        override fun run() {
            synchronized(this@HevcFrameDecoder) {
                if (closed || !active) return
                runCatching { drain() }.onFailure {
                    Log.w("OliviaHevcDecoder", "output_failed type=${it.javaClass.simpleName}")
                    onUnavailable()
                    return
                }
                outputHandler.postDelayed(this, OUTPUT_POLL_MS)
            }
        }
    }

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
            if (BuildConfig.DEBUG && (config.isNotEmpty() || packet.hasIrapNal())) {
                Log.i("OliviaHevcDecoder", "access_unit config=${config.isNotEmpty()} irap=${packet.hasIrapNal()} bytes=${packet.size}")
            }
            // Glasses can put VPS/SPS/PPS and the first keyframe in one access unit.
            // It is a picture packet as well: never discard it merely for containing CSD.
            if (!packet.hasPictureNal()) {
                // Initial CSD is supplied by configure/start. A later standalone configuration
                // (e.g. adaptive resolution) must still reach the running decoder.
                if (active && config.isNotEmpty()) {
                    queue(packet, presentationTimeUs, MediaCodec.BUFFER_FLAG_CODEC_CONFIG)
                }
                return
            }
            if (!active) {
                if (!packet.hasIrapNal()) return
                activate()
                // csd-0 is submitted automatically by MediaCodec.start(). Sending a second
                // codec-config buffer here violates that contract and can disrupt startup.
                active = true
                outputHandler.post(outputDrain)
            }
            queue(
                packet,
                presentationTimeUs,
                if (packet.hasIrapNal()) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0,
            )
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
        // Requests minimal buffering permitted by the codec; required reference/reorder
        // dependencies are still respected. Output must also be drained independently of input.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
        }
        cachedConfig?.let { format.setByteBuffer("csd-0", ByteBuffer.wrap(it)) }
        codec.configure(format, null, null, 0)
        codec.start()
    }

    private fun queue(packet: ByteArray, presentationTimeUs: Long, flags: Int) {
        val index = codec.dequeueInputBuffer(INPUT_TIMEOUT_US)
        if (index < 0) {
            if (BuildConfig.DEBUG) Log.w("OliviaHevcDecoder", "input_unavailable")
            return
        }
        val input = requireNotNull(codec.getInputBuffer(index))
        input.clear()
        input.put(packet)
        if (BuildConfig.DEBUG) {
            queuedAtNanos[presentationTimeUs] = System.nanoTime()
            if (queuedAtNanos.size > 128) queuedAtNanos.remove(queuedAtNanos.keys.first())
        }
        codec.queueInputBuffer(index, 0, packet.size, presentationTimeUs, flags)
    }

    private fun drain() {
        val info = MediaCodec.BufferInfo()
        while (true) {
            when (val index = codec.dequeueOutputBuffer(info, OUTPUT_TIMEOUT_US)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> return
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> if (BuildConfig.DEBUG) {
                    val format = codec.outputFormat
                    Log.i("OliviaHevcDecoder", "output_format width=${format.getInteger(MediaFormat.KEY_WIDTH)} " +
                        "height=${format.getInteger(MediaFormat.KEY_HEIGHT)}")
                }
                else -> if (index >= 0) {
                    try {
                        codec.getOutputImage(index)?.use { image ->
                            val outputAt = System.nanoTime()
                            val queuedAt = queuedAtNanos.remove(info.presentationTimeUs)
                            val crop = image.cropRect
                            val nv21 = image.toNv21()
                            if (BuildConfig.DEBUG) Log.i("OliviaHevcDecoder",
                                "decode_timing output=${outputCount++} codec_wait_ms=${queuedAt?.let { (outputAt - it) / 1_000_000 }} " +
                                    "convert_ms=${(System.nanoTime() - outputAt) / 1_000_000} pending=${queuedAtNanos.size}")
                            onFrame(crop.width(), crop.height(), nv21, info.presentationTimeUs)
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
        outputHandler.removeCallbacksAndMessages(null)
        if (active) runCatching { codec.stop() }
        runCatching { codec.release() }
        cachedConfig?.fill(0)
        cachedConfig = null
        queuedAtNanos.clear()
        outputThread.quitSafely()
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
        // Image.Plane.buffer is an Android getter, not a cheap field. Fetch each buffer/stride
        // once instead of calling into Image for every one of ~1.4 million output bytes.
        val yBuffer = y.buffer.duplicate()
        val yStart = yBuffer.position() + crop.top * y.rowStride + crop.left * y.pixelStride
        val yRowStride = y.rowStride
        val yPixelStride = y.pixelStride
        for (row in 0 until height) {
            val source = yStart + row * yRowStride
            val destination = row * width
            if (yPixelStride == 1) {
                yBuffer.position(source)
                yBuffer.get(output, destination, width)
            } else {
                for (column in 0 until width) output[destination + column] =
                    yBuffer.get(source + column * yPixelStride)
            }
        }
        val uBuffer = u.buffer
        val vBuffer = v.buffer
        val uRowStride = u.rowStride
        val vRowStride = v.rowStride
        val uPixelStride = u.pixelStride
        val vPixelStride = v.pixelStride
        val uStart = uBuffer.position() + crop.top / 2 * uRowStride + crop.left / 2 * uPixelStride
        val vStart = vBuffer.position() + crop.top / 2 * vRowStride + crop.left / 2 * vPixelStride
        var offset = width * height
        for (row in 0 until height / 2) {
            val uRow = uStart + row * uRowStride
            val vRow = vStart + row * vRowStride
            for (column in 0 until width / 2) {
                output[offset++] = vBuffer.get(vRow + column * vPixelStride)
                output[offset++] = uBuffer.get(uRow + column * uPixelStride)
            }
        }
        return output
    }

    private companion object {
        const val INPUT_TIMEOUT_US = 10_000L
        const val OUTPUT_TIMEOUT_US = 0L
        const val OUTPUT_POLL_MS = 5L
    }
}
