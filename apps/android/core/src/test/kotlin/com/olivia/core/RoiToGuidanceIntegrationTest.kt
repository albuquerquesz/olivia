package com.olivia.core

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RoiToGuidanceIntegrationTest {
    @Test
    fun `two ROI local analyses produce one qualitative correction without provider direction`() = runBlocking {
        val clock = RoiIntegrationClock()
        val response = """{"schemaVersion":"2.0","targetPresent":true,"confidence":0.92,"roi":{"x":0.72,"y":0.3,"width":0.2,"height":0.35}}"""
        val analyzer = GeminiFrameAnalyzer(
            clock = clock,
            transport = object : GeminiVisualTransport {
                override suspend fun classify(frame: Frame): Result<String> = Result.success(response)
            },
            localVisualAnalyzer = DeterministicLocalVisualAnalyzer(),
        )
        val guidance = DefaultGuidanceEngine(AudioMode.VOICE)

        val first = analyzer.analyze(frame(1, clock)).getOrThrow()
        assertNull(guidance.onAnalysis(first, clock.now()))
        clock.advanceBy(2_000)
        val second = analyzer.analyze(frame(2, clock)).getOrThrow()
        val correction = requireNotNull(guidance.onAnalysis(second, clock.now()))

        assertEquals(Direction.TURN_RIGHT, correction.direction)
        assertEquals("Vire para a direita.", correction.spokenText)
        assertEquals(AnalyzerSource.GEMINI_ROI_LOCAL, second.source)
    }

    private fun frame(sequence: Long, clock: OliviaClock): Frame = Frame(
        stamp = FrameStamp(CaptureId(SessionId("roi-loop"), 1), sequence, clock.now()),
        width = 80,
        height = 120,
        format = FrameFormat.RGBA_8888,
        origin = FrameOrigin.MOCK,
        bytes = ByteArray(80 * 120 * 4) { 0xff.toByte() },
    )
}

private class RoiIntegrationClock : OliviaClock {
    private var nowMs = 0L
    override fun now(): ClockReading = ClockReading(nowMs, 1_700_000_000_000 + nowMs)
    fun advanceBy(ms: Long) { nowMs += ms }
}
