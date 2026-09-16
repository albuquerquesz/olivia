package com.olivia.companion.ai

import android.graphics.Bitmap
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.FirebaseAI
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import com.google.firebase.appcheck.debug.testing.DebugAppCheckTestHelper
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Diagnostic-only decomposition of the pre-TTFT path. It never changes the production
 * transport, never logs content and intentionally keeps the 3.5 Flash-Lite control model.
 *
 * The default run uses twelve requests: one cold countTokens text call, three image token
 * counts, two text-only streams and two streams at each of 256/512/1024 px. The first request
 * is labelled cold; all later requests share the same Firebase model instance and are labelled
 * hot. This is a session-level distinction, not a claim about server-side cold starts.
 */
@RunWith(AndroidJUnit4::class)
class FirebasePreTtftBreakdownTest {
    private val arguments = InstrumentationRegistry.getArguments()
    private val debugAppCheck = DebugAppCheckTestHelper.fromInstrumentationArgs()

    @Test
    fun twelveSamplesBreakDownPreTtft() {
        assertEquals("true", required("oliviaLiveAiEnabled"))
        assertEquals("gemini-3.5-flash-lite", required("oliviaAiModel"))
        required("firebaseAppCheckDebugSecret")

        val appContext = InstrumentationRegistry.getInstrumentation().context
        val probeApp = FirebaseApp.initializeApp(
            appContext,
            FirebaseApp.getInstance().options,
            "firebase-pre-ttft-${UUID.randomUUID()}",
        )
        try {
            debugAppCheck.withDebugProvider<Exception>(probeApp) {
                runBlocking {
                    val model = FirebaseAI.getInstance(probeApp, GenerativeBackend.googleAI())
                        .generativeModel(
                            modelName = MODEL,
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
                    val failures = linkedMapOf<String, Int>()
                    var requestOrdinal = 0
                    var successfulRequests = 0
                    var coldRequest: TimedValue? = null
                    val tokenText = mutableListOf<TimedValue>()
                    val tokenImages = linkedMapOf<Int, MutableList<TimedValue>>()
                    val textStreams = mutableListOf<StreamSample>()
                    val imageStreams = linkedMapOf<Int, MutableList<StreamSample>>()

                    fun nextPhase(label: String): RequestPhase {
                        val ordinal = requestOrdinal++
                        return RequestPhase(label, ordinal == 0)
                    }

                    val textToken = runCountTokens(model, content { text(BASELINE_TEXT) }, nextPhase("count_text"), failures)
                    if (textToken != null) {
                        successfulRequests++
                        coldRequest = textToken
                        tokenText += textToken
                    }

                    IMAGE_SIZES.forEach { size ->
                        val jpeg = syntheticJpeg(size)
                        try {
                            val measured = runCountTokens(
                                model,
                                content {
                                    inlineData(jpeg, "image/jpeg")
                                    text(FirebaseGeminiVisualTransport.PROMPT)
                                },
                                nextPhase("count_image_$size"),
                                failures,
                            )
                            if (measured != null) {
                                successfulRequests++
                                tokenImages.getOrPut(size) { mutableListOf() } += measured.copy(bytes = jpeg.size)
                            }
                        } finally {
                            jpeg.fill(0)
                        }
                    }

                    repeat(TEXT_SAMPLE_COUNT) {
                        val measured = runStream(
                            model,
                            content { text(FirebaseGeminiVisualTransport.PROMPT) },
                            nextPhase("stream_text"),
                            failures,
                        )
                        if (measured != null) {
                            successfulRequests++
                            textStreams += measured
                        }
                    }

                    IMAGE_SIZES.forEach { size ->
                        val jpeg = syntheticJpeg(size)
                        try {
                            repeat(IMAGE_SAMPLE_COUNT) {
                                val measured = runStream(
                                    model,
                                    content {
                                        inlineData(jpeg, "image/jpeg")
                                        text(FirebaseGeminiVisualTransport.PROMPT)
                                    },
                                    nextPhase("stream_image_$size"),
                                    failures,
                                )
                                if (measured != null) {
                                    successfulRequests++
                                    imageStreams.getOrPut(size) { mutableListOf() } += measured.copy(bytes = jpeg.size)
                                }
                            }
                        } finally {
                            jpeg.fill(0)
                        }
                    }

                    val failureReport = failures.entries.joinToString(",") { "${it.key}:${it.value}" }
                    val report = buildString {
                        append("model=$MODEL requests=$requestOrdinal successes=$successfulRequests ")
                        append("failures=${requestOrdinal - successfulRequests} failure_codes=$failureReport ")
                        coldRequest?.let { append("first_request_cold_ms=${it.durationMs} ") }
                        append("count_text_ms=${formatTimed(tokenText)} ")
                        append("count_text_tokens=${formatInts(tokenText.map { it.tokens })} ")
                        tokenImages.forEach { (size, samples) ->
                            append("count_image_${size}_ms=${formatTimed(samples)} ")
                            append("count_image_${size}_bytes=${formatInts(samples.map { it.bytes ?: 0 })} ")
                            append("count_image_${size}_tokens=${formatInts(samples.map { it.tokens })} ")
                        }
                        append("stream_text_ttft_ms=${formatStream(textStreams) { it.ttftMs }} ")
                        append("stream_text_completion_ms=${formatStream(textStreams) { it.completionMs }} ")
                        imageStreams.forEach { (size, samples) ->
                            append("stream_image_${size}_bytes=${formatInts(samples.map { it.bytes ?: 0 })} ")
                            append("stream_image_${size}_ttft_ms=${formatStream(samples) { it.ttftMs }} ")
                            append("stream_image_${size}_completion_ms=${formatStream(samples) { it.completionMs }} ")
                        }
                        append("direct_api=SKIPPED_NO_EPHEMERAL_KEY agent_platform=SKIPPED_PROVIDER_NOT_ENABLED")
                    }
                    Log.i("OliviaBE07Breakdown", report.trim())
                    assertTrue("PRE_TTFT_NO_SUCCESS_$failureReport", successfulRequests > 0)
                }
            }
        } finally {
            probeApp.delete()
        }
    }

    private suspend fun runCountTokens(
        model: com.google.firebase.ai.GenerativeModel,
        request: Content,
        phase: RequestPhase,
        failures: MutableMap<String, Int>,
    ): TimedValue? {
        val started = System.nanoTime()
        return try {
            val response = withTimeout(DIAGNOSTIC_TIMEOUT_MS) { model.countTokens(request) }
            TimedValue(
                durationMs = elapsedMs(started),
                tokens = response.totalTokens,
                cold = phase.cold,
            )
        } catch (_: TimeoutCancellationException) {
            failures["${phase.label}_TIMEOUT"] = (failures["${phase.label}_TIMEOUT"] ?: 0) + 1
            null
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            failures["${phase.label}_TRANSPORT"] = (failures["${phase.label}_TRANSPORT"] ?: 0) + 1
            null
        }
    }

    private suspend fun runStream(
        model: com.google.firebase.ai.GenerativeModel,
        request: Content,
        phase: RequestPhase,
        failures: MutableMap<String, Int>,
    ): StreamSample? {
        val started = System.nanoTime()
        var firstChunk: Long? = null
        var lastChunk: Long? = null
        var chunks = 0
        return try {
            withTimeout(DIAGNOSTIC_TIMEOUT_MS) {
                model.generateContentStream(request).collect {
                    val now = System.nanoTime()
                    if (firstChunk == null) firstChunk = now
                    lastChunk = now
                    chunks++
                }
            }
            val first = firstChunk
            val last = lastChunk
            if (first == null || last == null || chunks == 0) {
                failures["${phase.label}_EMPTY"] = (failures["${phase.label}_EMPTY"] ?: 0) + 1
                null
            } else {
                StreamSample(
                    ttftMs = elapsedMs(started, first),
                    completionMs = elapsedMs(started, last),
                    chunks = chunks,
                    cold = phase.cold,
                )
            }
        } catch (_: TimeoutCancellationException) {
            failures["${phase.label}_TIMEOUT"] = (failures["${phase.label}_TIMEOUT"] ?: 0) + 1
            null
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            failures["${phase.label}_TRANSPORT"] = (failures["${phase.label}_TRANSPORT"] ?: 0) + 1
            null
        }
    }

    private fun syntheticJpeg(maxEdge: Int): ByteArray {
        val width = if (maxEdge >= 1024) 1024 else maxEdge
        val height = (width * 9 / 16).coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return try {
            bitmap.eraseColor(0xffeeeeee.toInt())
            val left = width / 5
            val top = height / 5
            val right = (width * 4 / 5).coerceAtLeast(left + 1)
            val bottom = (height * 4 / 5).coerceAtLeast(top + 1)
            for (y in top until bottom) {
                for (x in left until right) bitmap.setPixel(x, y, 0xff1565c0.toInt())
            }
            ByteArrayOutputStream().use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream))
                stream.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun required(key: String): String =
        arguments.getString(key)?.takeIf { it.isNotBlank() } ?: throw AssertionError("LIVE_CONFIG_MISSING")

    private fun elapsedMs(startedNanos: Long, finishedNanos: Long = System.nanoTime()): Long =
        TimeUnit.NANOSECONDS.toMillis(finishedNanos - startedNanos).coerceAtLeast(0)

    private fun formatTimed(samples: List<TimedValue>): String =
        formatLong(samples.map { it.durationMs })

    private fun formatStream(samples: List<StreamSample>, value: (StreamSample) -> Long): String =
        formatLong(samples.map(value))

    /** nearest-rank p50/p95, with count and empty state kept in the field itself. */
    private fun formatInts(values: List<Int>): String =
        formatLong(values.map(Int::toLong))

    private fun formatLong(values: List<Long>): String {
        if (values.isEmpty()) return "n=0"
        val sorted = values.sorted()
        fun percentile(p: Double): Long = sorted[(kotlin.math.ceil(sorted.size * p).toInt() - 1).coerceAtLeast(0)]
        return "n=${sorted.size},p50=${percentile(.5)},p95=${percentile(.95)}"
    }

    private data class RequestPhase(val label: String, val cold: Boolean)

    private data class TimedValue(
        val durationMs: Long,
        val tokens: Int,
        val bytes: Int? = null,
        val cold: Boolean,
    )

    private data class StreamSample(
        val ttftMs: Long,
        val completionMs: Long,
        val chunks: Int,
        val bytes: Int? = null,
        val cold: Boolean,
    )

    private companion object {
        const val MODEL = "gemini-3.5-flash-lite"
        const val JPEG_QUALITY = 80
        const val DIAGNOSTIC_TIMEOUT_MS = 30_000L
        const val TEXT_SAMPLE_COUNT = 2
        const val IMAGE_SAMPLE_COUNT = 2
        val IMAGE_SIZES = listOf(256, 512, 1024)
        const val BASELINE_TEXT = "Return the single JSON object required by Olivia."
    }
}
