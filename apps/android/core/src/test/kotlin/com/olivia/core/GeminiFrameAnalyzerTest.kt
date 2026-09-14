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
        val analyzer = GeminiFrameAnalyzer(clock, replyTransport(validReply))

        val result = analyzer.analyze(frame(clock))

        assertTrue(result.isSuccess)
        assertEquals(AnalyzerSource.GEMINI, result.getOrThrow().source)
        assertEquals(Direction.TURN_RIGHT, result.getOrThrow().diagnoses.single().direction)
    }

    @Test
    fun `classifies transport and schema failures without exposing content`() = runBlocking {
        val clock = AnalyzerTestClock(2_000)
        val transportFailure = GeminiFrameAnalyzer(clock, object : GeminiVisualTransport {
            override suspend fun classify(frame: Frame): Result<String> = Result.failure(IllegalStateException("secret"))
        }).analyze(frame(clock))
        val schemaFailure = GeminiFrameAnalyzer(clock, replyTransport("not json")).analyze(frame(clock))

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
        }, timeoutMs = 1)

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
        format = FrameFormat.JPEG,
        origin = FrameOrigin.MOCK,
        bytes = byteArrayOf(1),
    )

    private companion object {
        const val validReply = """{"schemaVersion":"1.0","diagnoses":[{"kind":"FRAMING","severity":"MAJOR","confidence":0.9,"direction":"TURN_RIGHT","magnitude":null}]}"""
    }
}

private class AnalyzerTestClock(private val time: Long) : OliviaClock {
    override fun now(): ClockReading = ClockReading(time, 1_700_000_000_000 + time)
}
