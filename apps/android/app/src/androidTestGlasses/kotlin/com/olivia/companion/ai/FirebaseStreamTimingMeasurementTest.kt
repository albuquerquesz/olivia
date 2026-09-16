package com.olivia.companion.ai

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.FirebaseAI
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import com.google.firebase.appcheck.debug.testing.DebugAppCheckTestHelper
import com.olivia.core.Frame
import com.olivia.core.FrameFormat
import com.olivia.core.FrameOrigin
import com.olivia.core.FrameStamp
import com.olivia.core.CaptureId
import com.olivia.core.SessionId
import com.olivia.core.SystemOliviaClock
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Diagnostic-only stream timing probe; it never changes the production one-shot analyzer. */
@RunWith(AndroidJUnit4::class)
class FirebaseStreamTimingMeasurementTest {
    private val arguments = InstrumentationRegistry.getArguments()
    private val debugAppCheck = DebugAppCheckTestHelper.fromInstrumentationArgs()

    @Test
    fun tenGeminiStreamSamplesPrintTtftAndCompletion() {
        assertEquals("true", required("oliviaLiveAiEnabled"))
        // Keep this experiment on the submitted control model; no 3.1 comparison is mixed in.
        assertEquals("gemini-3.5-flash-lite", required("oliviaAiModel"))
        required("firebaseAppCheckDebugSecret")

        val firebaseContext = InstrumentationRegistry.getInstrumentation().context
        val probeApp = FirebaseApp.initializeApp(
            firebaseContext,
            FirebaseApp.getInstance().options,
            "firebase-stream-probe-${UUID.randomUUID()}",
        )
        try {
            debugAppCheck.withDebugProvider<Exception>(probeApp) {
                runBlocking {
                    val clock = SystemOliviaClock()
                    val model = FirebaseAI.getInstance(probeApp, GenerativeBackend.googleAI())
                        .generativeModel(
                            modelName = "gemini-3.5-flash-lite",
                            generationConfig = generationConfig {
                                temperature = 0f
                                maxOutputTokens = 128
                                responseMimeType = "application/json"
                                responseSchema = FirebaseGeminiVisualTransport.WORK_AREA_RESPONSE_SCHEMA
                                thinkingConfig = thinkingConfig {
                                    thinkingLevel = ThinkingLevel.MINIMAL
                                }
                            },
                        )
                    val ttft = mutableListOf<Long>()
                    val completion = mutableListOf<Long>()
                    val chunkCounts = mutableListOf<Long>()
                    val failures = linkedMapOf<String, Int>()

                    repeat(SAMPLE_COUNT) { index ->
                        val frame = syntheticFrame(clock, index.toLong())
                        try {
                            var firstChunkNanos: Long? = null
                            var lastChunkNanos: Long? = null
                            var chunks = 0L
                            val started = System.nanoTime()
                            withTimeout(DIAGNOSTIC_TIMEOUT_MS) {
                                model.generateContentStream(
                                    content {
                                        inlineData(frame.bytes, "image/jpeg")
                                        text(FirebaseGeminiVisualTransport.PROMPT)
                                    },
                                ).collect {
                                    val now = System.nanoTime()
                                    if (firstChunkNanos == null) firstChunkNanos = now
                                    lastChunkNanos = now
                                    chunks++
                                }
                            }
                            val first = firstChunkNanos
                            val last = lastChunkNanos
                            if (first == null || last == null || chunks == 0L) {
                                failures["EMPTY_STREAM"] = (failures["EMPTY_STREAM"] ?: 0) + 1
                            } else {
                                ttft += elapsedMs(started, first)
                                completion += elapsedMs(started, last)
                                chunkCounts += chunks
                            }
                        } catch (_: TimeoutCancellationException) {
                            failures["TIMEOUT"] = (failures["TIMEOUT"] ?: 0) + 1
                        } catch (_: Throwable) {
                            failures["TRANSPORT"] = (failures["TRANSPORT"] ?: 0) + 1
                        } finally {
                            frame.bytes.fill(0)
                        }
                    }

                    val failureReport = failures.entries.joinToString(",") { "${it.key}:${it.value}" }
                    val successes = ttft.size
                    android.util.Log.i(
                        "OliviaBE07Stream",
                        "model=gemini-3.5-flash-lite samples=$SAMPLE_COUNT successes=$successes " +
                            "failures=${SAMPLE_COUNT - successes} failure_codes=$failureReport " +
                            "ttft_p50_ms=${percentile(ttft, .5)} ttft_p95_ms=${percentile(ttft, .95)} " +
                            "stream_completion_p50_ms=${percentile(completion, .5)} " +
                            "stream_completion_p95_ms=${percentile(completion, .95)} " +
                            "stream_chunks_p50=${percentile(chunkCounts, .5)} " +
                            "stream_chunks_p95=${percentile(chunkCounts, .95)}",
                    )
                    assertTrue("STREAM_NO_SUCCESS_$failureReport", successes > 0)
                }
            }
        } finally {
            probeApp.delete()
        }
    }

    private fun required(key: String): String =
        arguments.getString(key)?.takeIf { it.isNotBlank() } ?: throw AssertionError("LIVE_CONFIG_MISSING")

    private fun syntheticFrame(clock: SystemOliviaClock, sequence: Long): Frame {
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        return try {
            bitmap.eraseColor(0xffeeeeee.toInt())
            for (y in 160 until 400) {
                for (x in 280 until 460) bitmap.setPixel(x, y, 0xff1565c0.toInt())
            }
            val bytes = ByteArrayOutputStream().use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream))
                stream.toByteArray()
            }
            Frame(
                FrameStamp(CaptureId(SessionId("be07-stream"), 1), sequence, clock.now()),
                512,
                512,
                FrameFormat.JPEG,
                FrameOrigin.MOCK,
                bytes,
            )
        } finally {
            bitmap.recycle()
        }
    }

    private fun elapsedMs(startedNanos: Long, finishedNanos: Long): Long =
        TimeUnit.NANOSECONDS.toMillis(finishedNanos - startedNanos).coerceAtLeast(0)

    private fun percentile(values: List<Long>, p: Double): Long {
        if (values.isEmpty()) return 0
        val sorted = values.sorted()
        return sorted[(kotlin.math.ceil(sorted.size * p).toInt() - 1).coerceAtLeast(0)]
    }

    private companion object {
        const val SAMPLE_COUNT = 10
        const val DIAGNOSTIC_TIMEOUT_MS = 30_000L
    }
}
