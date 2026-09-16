package com.olivia.companion.ai

import android.graphics.Bitmap
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.debug.testing.DebugAppCheckTestHelper
import com.olivia.companion.audio.AndroidAudioFeedback
import com.olivia.core.AnalyzerSource
import com.olivia.core.AnalysisFailure
import com.olivia.core.AnalysisTimingSink
import com.olivia.core.AudioMode
import com.olivia.core.CaptureId
import com.olivia.core.Confidence
import com.olivia.core.Correction
import com.olivia.core.DeterministicLocalVisualAnalyzer
import com.olivia.core.Direction
import com.olivia.core.Frame
import com.olivia.core.FrameFormat
import com.olivia.core.FrameOrigin
import com.olivia.core.FrameStamp
import com.olivia.core.GeminiFrameAnalyzer
import com.olivia.core.Severity
import com.olivia.core.SessionId
import com.olivia.core.SystemOliviaClock
import com.olivia.core.VisualDiagnosis
import com.olivia.core.DiagnosisKind
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Ten-sample BE-07 measurement on a real Android device; output is aggregate-only. */
@RunWith(AndroidJUnit4::class)
class FirebaseLiveLoopMeasurementTest {
    private val arguments = InstrumentationRegistry.getArguments()
    private val debugAppCheck = DebugAppCheckTestHelper.fromInstrumentationArgs()

    @Test
    fun tenGeminiToVoiceSamplesPrintSanitizedReport() {
        assertEquals("true", required("oliviaLiveAiEnabled"))
        val model = required("oliviaAiModel")
        required("firebaseAppCheckDebugSecret")
        val firebaseContext = InstrumentationRegistry.getInstrumentation().context
        val audioContext = InstrumentationRegistry.getInstrumentation().targetContext
        val probeApp = FirebaseApp.initializeApp(firebaseContext, FirebaseApp.getInstance().options,
            "firebase-measurement-${UUID.randomUUID()}")
        try {
            debugAppCheck.withDebugProvider<Exception>(probeApp) {
                runBlocking {
                    val clock = SystemOliviaClock()
                    val stageSamples = linkedMapOf<String, MutableList<Long>>()
                    val recordStage: (String, Long) -> Unit = { stage, durationMs ->
                        synchronized(stageSamples) {
                            stageSamples.getOrPut(stage) { mutableListOf() }.add(durationMs)
                        }
                    }
                    val analyzer = GeminiFrameAnalyzer(
                        clock = clock,
                        transport = FirebaseGeminiVisualTransport.forFirebaseApp(
                            app = probeApp,
                            modelName = model,
                            timingSink = FirebaseTimingSink { stage, durationMs ->
                                recordStage("firebase_${stage.name.lowercase()}", durationMs)
                            },
                        ),
                        localVisualAnalyzer = DeterministicLocalVisualAnalyzer(),
                        lumaDecoder = AndroidFrameLumaDecoder(),
                        timeoutMs = 30_000,
                        timingSink = AnalysisTimingSink { stage, durationMs ->
                            recordStage("core_${stage.name.lowercase()}", durationMs)
                        },
                    )
                    val audio = AndroidAudioFeedback(audioContext, clock, AudioMode.VOICE)
                    try {
                        val inference = mutableListOf<Long>()
                        val frameToAudio = mutableListOf<Long>()
                        repeat(SAMPLE_COUNT) { index ->
                            val frame = syntheticFrame(clock, index.toLong())
                            val receivedAt = clock.now().monotonicMs
                            val started = receivedAt
                            val result = analyzer.analyze(frame)
                            val finished = clock.now().monotonicMs
                            assertTrue(
                                "GEMINI_SAMPLE_${index + 1}_FAILED_${failureCode(result)}",
                                result.isSuccess,
                            )
                            val analysis = result.getOrThrow()
                            assertEquals(AnalyzerSource.GEMINI_ROI_LOCAL, analysis.source)
                            val correction = Correction(
                                id = "measurement-$index",
                                capture = frame.stamp.capture,
                                issuedAt = clock.now(),
                                diagnosis = VisualDiagnosis(
                                    DiagnosisKind.FRAMING,
                                    Severity.MAJOR,
                                    Confidence(0.9f),
                                    Direction.TURN_RIGHT,
                                ),
                                direction = Direction.TURN_RIGHT,
                                confidence = Confidence(0.9f),
                                spokenText = "Vire para a direita.",
                            )
                            val receipt = audio.announce(correction).getOrThrow()
                            inference += finished - started
                            frameToAudio += receipt.startedAt.monotonicMs - receivedAt
                        }
                        val stageReport = stageSamples.entries.joinToString(" ") { (stage, samples) ->
                            "${stage}_p50_ms=${percentile(samples, .5)} ${stage}_p95_ms=${percentile(samples, .95)}"
                        }
                        Log.i("OliviaBE07", "samples=$SAMPLE_COUNT inference_p50_ms=${percentile(inference, .5)} " +
                            "inference_p95_ms=${percentile(inference, .95)} " +
                            "frame_to_audio_p50_ms=${percentile(frameToAudio, .5)} " +
                            "frame_to_audio_p95_ms=${percentile(frameToAudio, .95)} $stageReport")
                    } finally {
                        audio.close()
                    }
                }
            }
        } finally {
            probeApp.delete()
        }
    }

    private fun required(key: String): String = arguments.getString(key)?.takeIf { it.isNotBlank() }
        ?: throw AssertionError("LIVE_CONFIG_MISSING")

    private fun failureCode(result: Result<*>): String =
        (result.exceptionOrNull() as? AnalysisFailure)?.code?.name ?: "UNKNOWN"

    private fun percentile(values: List<Long>, p: Double): Long {
        val sorted = values.sorted()
        return sorted[(kotlin.math.ceil(sorted.size * p).toInt() - 1).coerceAtLeast(0)]
    }

    private fun syntheticFrame(clock: SystemOliviaClock, sequence: Long): Frame {
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        return try {
            bitmap.eraseColor(0xffeeeeee.toInt())
            for (y in 160 until 400) for (x in 280 until 460) bitmap.setPixel(x, y, 0xff1565c0.toInt())
            val bytes = ByteArrayOutputStream().use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)); stream.toByteArray()
            }
            Frame(FrameStamp(CaptureId(SessionId("be07"), 1), sequence, clock.now()), 512, 512,
                FrameFormat.JPEG, FrameOrigin.MOCK, bytes)
        } finally { bitmap.recycle() }
    }

    private companion object { const val SAMPLE_COUNT = 10 }
}
