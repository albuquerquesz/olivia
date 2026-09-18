package com.olivia.companion.transcription

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Decodes a media file's first audio track into mono 16 kHz float PCM, the format
 * whisper.cpp's `whisper_full` expects. Runs post-STOPPED on an already-imported
 * local file; never touches a live frame or microphone stream.
 */
internal object AudioDecoder {
    private const val TARGET_SAMPLE_RATE = 16_000
    private const val TIMEOUT_US = 10_000L
    private const val PCM_SCALE = 32_768f

    fun decodeToMonoPcm16k(path: String): FloatArray {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(path)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw IllegalStateException("Nenhuma faixa de áudio na mídia")
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = requireNotNull(format.getString(MediaFormat.KEY_MIME))
            val sourceSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            val codec = MediaCodec.createDecoderByType(mime)
            return try {
                codec.configure(format, null, null, 0)
                codec.start()
                val pcm = decodeLoop(extractor, codec)
                val mono = downmixToMono(pcm, channelCount)
                resample(mono, sourceSampleRate, TARGET_SAMPLE_RATE)
            } finally {
                codec.stop()
                codec.release()
            }
        } finally {
            extractor.release()
        }
    }

    private fun decodeLoop(extractor: MediaExtractor, codec: MediaCodec): ShortArray {
        val output = GrowableShortBuffer()
        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false

        while (!outputDone) {
            if (!inputDone) {
                val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inputIndex >= 0) {
                    val inputBuffer = requireNotNull(codec.getInputBuffer(inputIndex))
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            var outputIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            while (outputIndex >= 0) {
                if (bufferInfo.size > 0) {
                    val outputBuffer = requireNotNull(codec.getOutputBuffer(outputIndex))
                    outputBuffer.order(ByteOrder.LITTLE_ENDIAN)
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                    val shortBuffer = outputBuffer.asShortBuffer()
                    output.append(shortBuffer)
                }
                val endOfStream = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                codec.releaseOutputBuffer(outputIndex, false)
                if (endOfStream) {
                    outputDone = true
                    break
                }
                outputIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            }
        }
        return output.toShortArray()
    }

    private fun downmixToMono(pcm: ShortArray, channelCount: Int): FloatArray {
        if (channelCount <= 1) return FloatArray(pcm.size) { pcm[it] / PCM_SCALE }
        val frameCount = pcm.size / channelCount
        return FloatArray(frameCount) { frame ->
            var sum = 0f
            for (channel in 0 until channelCount) sum += pcm[frame * channelCount + channel]
            (sum / channelCount) / PCM_SCALE
        }
    }

    /** Linear-interpolation resampler; sufficient for feeding an STT model, not for playback. */
    private fun resample(mono: FloatArray, sourceRate: Int, targetRate: Int): FloatArray {
        if (sourceRate == targetRate || mono.isEmpty()) return mono
        val ratio = targetRate.toDouble() / sourceRate.toDouble()
        val targetLength = (mono.size * ratio).roundToInt()
        return FloatArray(targetLength) { index ->
            val sourcePosition = index / ratio
            val left = sourcePosition.toInt().coerceIn(0, mono.size - 1)
            val right = (left + 1).coerceAtMost(mono.size - 1)
            val fraction = (sourcePosition - left).toFloat()
            mono[left] + (mono[right] - mono[left]) * fraction
        }
    }
}

/** Avoids boxing ~millions of Short samples through a List<Short> for a 30-60s take. */
private class GrowableShortBuffer(initialCapacity: Int = 16 * 1024) {
    private var buffer = ShortArray(initialCapacity)
    private var size = 0

    fun append(shorts: java.nio.ShortBuffer) {
        val remaining = shorts.remaining()
        ensureCapacity(size + remaining)
        shorts.get(buffer, size, remaining)
        size += remaining
    }

    private fun ensureCapacity(minCapacity: Int) {
        if (minCapacity <= buffer.size) return
        var newCapacity = buffer.size * 2
        while (newCapacity < minCapacity) newCapacity *= 2
        buffer = buffer.copyOf(newCapacity)
    }

    fun toShortArray(): ShortArray = buffer.copyOf(size)
}
