package com.olivia.core

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeminiFrameAnalyzerTest {
    @Test
    fun `maps a validated transport reply and attributes Gemini`() = runBlocking {
        val clock = AnalyzerTestClock(2_000)
        val analyzer = GeminiFrameAnalyzer(clock, replyTransport(validReply), DeterministicLocalVisualAnalyzer())

        val result = analyzer.analyze(frame(clock))

        assertTrue(result.isSuccess)
        assertEquals(AnalyzerSource.GEMINI_ROI_LOCAL, result.getOrThrow().source)
        assertEquals(Direction.TURN_RIGHT, result.getOrThrow().diagnoses.first { it.kind == DiagnosisKind.FRAMING }.direction)
    }

    @Test
    fun `classifies transport and schema failures without exposing content`() = runBlocking {
        val clock = AnalyzerTestClock(2_000)
        val transportFailure = GeminiFrameAnalyzer(clock, object : GeminiVisualTransport {
            override suspend fun classify(frame: Frame): Result<String> = Result.failure(IllegalStateException("secret"))
        }, DeterministicLocalVisualAnalyzer()).analyze(frame(clock))
        val schemaFailure = GeminiFrameAnalyzer(clock, replyTransport("not json"), DeterministicLocalVisualAnalyzer()).analyze(frame(clock))

        assertFalse(transportFailure.isSuccess)
        assertEquals(AnalysisFailureCode.TRANSPORT, (transportFailure.exceptionOrNull() as AnalysisFailure).code)
        assertFalse(schemaFailure.isSuccess)
        assertEquals(AnalysisFailureCode.SCHEMA, (schemaFailure.exceptionOrNull() as AnalysisFailure).code)
    }

    @Test
    fun `times out rather than retrying the same frame`() = runBlocking {
        val clock = AnalyzerTestClock(2_000)
        val analyzer = GeminiFrameAnalyzer(clock, object : GeminiVisualTransport {
            override suspend fun classify(frame: Frame): Result<String> {
                delay(50)
                return Result.success(validReply)
            }
        }, DeterministicLocalVisualAnalyzer(), timeoutMs = 1)

        val result = analyzer.analyze(frame(clock))

        assertFalse(result.isSuccess)
        assertEquals(AnalysisFailureCode.TIMEOUT, (result.exceptionOrNull() as AnalysisFailure).code)
    }

    private fun replyTransport(reply: String): GeminiVisualTransport = object : GeminiVisualTransport {
        override suspend fun classify(frame: Frame): Result<String> = Result.success(reply)
    }

    private fun frame(clock: OliviaClock): Frame = Frame(
        stamp = FrameStamp(CaptureId(SessionId("analysis"), 1), 0, clock.now()),
        width = 720,
        height = 1_280,
        format = FrameFormat.RGBA_8888,
        origin = FrameOrigin.MOCK,
        bytes = ByteArray(720 * 1_280 * 4) { if (it % 4 == 3) 0xff.toByte() else 0xff.toByte() },
    )

    private companion object {
        const val validReply = """{"schemaVersion":"2.0","targetPresent":true,"confidence":0.9,"roi":{"x":0.65,"y":0.25,"width":0.2,"height":0.4}}"""
    }
}

private class AnalyzerTestClock(private val time: Long) : OliviaClock {
    override fun now(): ClockReading = ClockReading(time, 1_700_000_000_000 + time)
}
