package com.olivia.companion.transcription

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.olivia.core.RecordingAsset
import com.olivia.core.SessionId
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real-hardware probe for BE-12/BE-08: decodes a bundled pt-BR speech clip and runs it
 * through the vendored whisper.cpp build. Confirms the native model loads from assets,
 * the MediaCodec decode path produces valid PCM, and the language stays forced to pt
 * (see app/src/main/jni/whisper/jni.c). Never touches a live frame or microphone.
 */
@RunWith(AndroidJUnit4::class)
class WhisperLocalTranscriptProviderInstrumentationTest {
    @Test
    fun transcribesBundledPortugueseSpeechClip() {
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext

        val localCopy = File(targetContext.cacheDir, "whisper-probe.m4a")
        testContext.assets.open("whisper-probe/vire-direita.m4a").use { input ->
            localCopy.outputStream().use { output -> input.copyTo(output) }
        }

        val provider = WhisperLocalTranscriptProvider(
            context = targetContext,
            resolveLocalPath = { localCopy.absolutePath },
        )
        val recording = RecordingAsset(
            id = "whisper-probe",
            sessionId = SessionId("whisper-probe-session"),
            startedAtEpochMs = 0L,
            durationMs = 2_300L,
            sizeBytes = localCopy.length(),
            hasAudioTrack = true,
        )

        val result = runBlocking { provider.transcribe(recording) }

        val segments = result.getOrThrow()
        val fullText = segments.joinToString(" ") { it.text }.lowercase()
        assertTrue("Expected at least one transcribed segment, got none", segments.isNotEmpty())
        assertTrue(
            "Expected recognizable Portuguese words in '$fullText'",
            listOf("vire", "direita", "graus").any { fullText.contains(it) },
        )
    }

    /**
     * Not a correctness check — a scaling probe. Confirms inferMs stays proportionate to a
     * ~15s clip (closer to a real 30-60s take) instead of only ever measuring a 2.3s phrase.
     * Read the actual numbers from logcat tag WhisperTranscript; this only checks it finishes.
     */
    @Test
    fun transcribesLongerClipWithoutFailing() {
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext

        val localCopy = File(targetContext.cacheDir, "whisper-probe-long.m4a")
        testContext.assets.open("whisper-probe/long-sample.m4a").use { input ->
            localCopy.outputStream().use { output -> input.copyTo(output) }
        }

        val provider = WhisperLocalTranscriptProvider(
            context = targetContext,
            resolveLocalPath = { localCopy.absolutePath },
        )
        val recording = RecordingAsset(
            id = "whisper-probe-long",
            sessionId = SessionId("whisper-probe-long-session"),
            startedAtEpochMs = 0L,
            durationMs = 15_250L,
            sizeBytes = localCopy.length(),
            hasAudioTrack = true,
        )

        val result = runBlocking { provider.transcribe(recording) }
        assertTrue("Expected at least one transcribed segment, got none", result.getOrThrow().isNotEmpty())
    }
}
