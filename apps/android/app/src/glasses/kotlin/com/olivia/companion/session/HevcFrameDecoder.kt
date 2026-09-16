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
 * Local-only HEVC decoder for DAT's compressed camera stream. Output is a short-lived NV21 frame;
 * no image, encoded packet, or decoder output is written to disk.
 */
internal class HevcFrameDecoder(
    width: Int,
    height: Int,
    private val onFrame: (width: Int, height: Int, nv21: ByteArray) -> Unit,
    private val onUnavailable: () -> Unit,
) : AutoCloseable {
    private val thread = HandlerThread("OliviaHevcDecoder").apply { start() }
    private val handler = Handler(thread.looper)
    private val imageReader = ImageReader.newInstance(width, height, ImageFormat.YUV_420_888, MAX_IMAGES)
    private val codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_HEVC)
    private var closed = false

    init {
        imageReader.setOnImageAvailableListener({ reader ->
            reader.acquireLatestImage()?.use { image ->
                if (!closed) runCatching { onFrame(image.width, image.height, image.toNv21()) }
                    .onFailure { onUnavailable() }
            }
        }, handler)
        runCatching {
            codec.configure(
                MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, width, height),
                imageReader.surface,
                null,
                0,
            )
            codec.start()
        }.onFailure {
            close()
            onUnavailable()
        }
    }

    fun enqueue(buffer: ByteBuffer, presentationTimeUs: Long, isCodecConfig: Boolean) {
        if (closed) return
        runCatching {
            val index = codec.dequeueInputBuffer(INPUT_TIMEOUT_US)
            if (index < 0) return
            val input = requireNotNull(codec.getInputBuffer(index))
            input.clear()
            val source = buffer.duplicate()
            input.put(source)
            codec.queueInputBuffer(
                index,
                0,
                input.position(),
                presentationTimeUs,
                if (isCodecConfig) MediaCodec.BUFFER_FLAG_CODEC_CONFIG else 0,
            )
            drain()
        }.onFailure { onUnavailable() }
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
        runCatching { codec.stop() }
        runCatching { codec.release() }
        imageReader.close()
        thread.quitSafely()
    }

    private fun Image.toNv21(): ByteArray {
        val output = ByteArray(width * height * 3 / 2)
        val y = planes[0]
        val u = planes[1]
        val v = planes[2]
        copyPlane(y, width, height, output, 0, 1)
        var offset = width * height
        for (row in 0 until height / 2) {
            for (column in 0 until width / 2) {
                output[offset++] = v.byteAt(row, column)
                output[offset++] = u.byteAt(row, column)
            }
        }
        return output
    }

    private fun copyPlane(
        plane: Image.Plane,
        columns: Int,
        rows: Int,
        output: ByteArray,
        start: Int,
        step: Int,
    ) {
        var offset = start
        for (row in 0 until rows) for (column in 0 until columns) {
            output[offset] = plane.byteAt(row, column)
            offset += step
        }
    }

    private fun Image.Plane.byteAt(row: Int, column: Int): Byte =
        buffer.get(row * rowStride + column * pixelStride)

    private companion object {
        const val MAX_IMAGES = 2
        const val INPUT_TIMEOUT_US = 10_000L
        const val OUTPUT_TIMEOUT_US = 0L
    }
}
