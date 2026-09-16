package com.olivia.companion.session

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioCueAssetTest {
    @Test
    fun versionedCuesArePcmStereoSixteenKilohertzAndApproximatelyEightyMilliseconds() {
        val rawDirectory = findRawDirectory()
        CUE_NAMES.forEach { name ->
            val wave = readWave(File(rawDirectory, "$name.wav"))
            assertEquals("$name must be PCM", 1, wave.audioFormat)
            assertEquals("$name must be stereo", 2, wave.channels)
            assertEquals("$name must be 16 kHz", 16_000, wave.sampleRate)
            assertEquals("$name must be PCM16", 16, wave.bitsPerSample)
            assertTrue("$name must be about 80 ms", wave.durationMs in 70L..90L)
        }

        val left = readWave(File(rawDirectory, "cue_direction_left.wav"))
        val right = readWave(File(rawDirectory, "cue_direction_right.wav"))
        assertTrue("left cue must favor the left channel", left.leftPeak > left.rightPeak)
        assertTrue("right cue must favor the right channel", right.rightPeak > right.leftPeak)
    }

    private fun findRawDirectory(): File = listOf(
        File("app/src/main/res/raw"),
        File("apps/android/app/src/main/res/raw"),
        File(System.getProperty("user.dir"), "app/src/main/res/raw"),
        File(System.getProperty("user.dir"), "src/main/res/raw"),
    ).firstOrNull { it.isDirectory } ?: error("Diretório de cues WAV não encontrado")

    private fun readWave(file: File): Wave {
        require(file.isFile) { "Cue ausente: ${file.path}" }
        val bytes = file.readBytes()
        require(bytes.size >= 44) { "Cue WAV inválido: ${file.name}" }
        require(bytes.copyOfRange(0, 4).contentEquals(ASCII_RIFF)) { "Cue sem RIFF: ${file.name}" }
        require(bytes.copyOfRange(8, 12).contentEquals(ASCII_WAVE)) { "Cue sem WAVE: ${file.name}" }

        var audioFormat = 0
        var channels = 0
        var sampleRate = 0
        var bitsPerSample = 0
        var dataOffset = -1
        var dataSize = 0
        var offset = 12
        while (offset + 8 <= bytes.size) {
            val chunkSize = littleEndianInt(bytes, offset + 4)
            val payloadOffset = offset + 8
            require(chunkSize >= 0 && payloadOffset <= bytes.size) { "Chunk WAV inválido: ${file.name}" }
            val payloadSize = chunkSize.coerceAtMost(bytes.size - payloadOffset)
            when {
                bytes.copyOfRange(offset, offset + 4).contentEquals(ASCII_FMT) -> {
                    require(payloadSize >= 16) { "Chunk fmt inválido: ${file.name}" }
                    audioFormat = littleEndianShort(bytes, payloadOffset)
                    channels = littleEndianShort(bytes, payloadOffset + 2)
                    sampleRate = littleEndianInt(bytes, payloadOffset + 4)
                    bitsPerSample = littleEndianShort(bytes, payloadOffset + 14)
                }
                bytes.copyOfRange(offset, offset + 4).contentEquals(ASCII_DATA) -> {
                    dataOffset = payloadOffset
                    dataSize = payloadSize
                    break
                }
            }
            offset = payloadOffset + chunkSize + (chunkSize and 1)
        }

        val bytesPerFrame = channels * (bitsPerSample / 8)
        require(bytesPerFrame > 0 && sampleRate > 0 && dataOffset >= 0 && dataSize > 0) {
            "Dados PCM ausentes: ${file.name}"
        }
        val pcm = bytes.copyOfRange(dataOffset, dataOffset + dataSize)
        return Wave(
            audioFormat = audioFormat,
            channels = channels,
            sampleRate = sampleRate,
            bitsPerSample = bitsPerSample,
            durationMs = pcm.size.toLong() * 1_000L / bytesPerFrame / sampleRate,
            leftPeak = channelPeak(pcm, channel = 0),
            rightPeak = channelPeak(pcm, channel = 1),
        )
    }

    private fun channelPeak(pcm: ByteArray, channel: Int): Int {
        val bytesPerFrame = 4
        var peak = 0
        var offset = channel * 2
        while (offset + 1 < pcm.size) {
            val sample = ByteBuffer.wrap(pcm, offset, 2)
                .order(ByteOrder.LITTLE_ENDIAN)
                .short
                .toInt()
            peak = maxOf(peak, abs(sample))
            offset += bytesPerFrame
        }
        return peak
    }

    private fun littleEndianShort(bytes: ByteArray, offset: Int): Int =
        ByteBuffer.wrap(bytes, offset, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xffff

    private fun littleEndianInt(bytes: ByteArray, offset: Int): Int =
        ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int

    private data class Wave(
        val audioFormat: Int,
        val channels: Int,
        val sampleRate: Int,
        val bitsPerSample: Int,
        val durationMs: Long,
        val leftPeak: Int,
        val rightPeak: Int,
    )

    private companion object {
        val ASCII_RIFF = "RIFF".toByteArray()
        val ASCII_WAVE = "WAVE".toByteArray()
        val ASCII_FMT = "fmt ".toByteArray()
        val ASCII_DATA = "data".toByteArray()
        val CUE_NAMES = listOf(
            "cue_direction_left",
            "cue_direction_right",
            "cue_direction_neutral",
            "cue_session_started",
            "cue_session_paused",
            "cue_session_resumed",
            "cue_session_stopped",
            "cue_connection_lost",
            "cue_connection_restored",
            "cue_voice_command_error",
            "cue_voice_input_unavailable",
        )
    }
}
