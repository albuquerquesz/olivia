package com.olivia.core

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Offline composition used by the Docker/JVM integration task. The source deliberately offers
 * frames faster than the analyzer can consume; no image bytes or SDK types leave this test.
 */
class SamplerPipelineIntegrationTest {
    @Test
    fun `fast source retains only the newest frame before the next sampled analysis`(): Unit = runBlocking {
        val clock = PipelineClock()
        val capture = CaptureId(SessionId("pipeline"), 1)
        val sampler = FrameSampler(clock)
        val analyzer = MockFrameAnalyzer(
            clock,
            mapOf(
                0L to MockAnalysisReply.Diagnosis(MockVisualFixtures.subjectRight.expectedDiagnoses),
                2L to MockAnalysisReply.Diagnosis(MockVisualFixtures.centered.expectedDiagnoses),
            ),
        )

        val first = assertIs<SamplingDecision.Analyze>(sampler.offer(frame(capture, 0, clock), ::accept).single())
        assertEquals(Direction.TURN_RIGHT, analyzer.analyze(first.frame).getOrThrow().diagnoses.single().direction)

        assertIs<SamplingDecision.Waiting>(sampler.offer(frame(capture, 1, clock), ::accept).single())
        val replacement = sampler.offer(frame(capture, 2, clock), ::accept)
        assertEquals(1L, assertIs<SamplingDecision.Discard>(replacement.first()).frame.stamp.sequence)
        assertEquals(FrameDiscardReason.REPLACED_BY_NEWER_FRAME, assertIs<SamplingDecision.Discard>(replacement.first()).reason)

        assertIs<SamplingDecision.Waiting>(sampler.completeAnalysis().single())
        clock.advanceBy(2_000)
        val newest = assertIs<SamplingDecision.Analyze>(sampler.poll().single())
        val recovered = analyzer.analyze(newest.frame).getOrThrow()

        assertEquals(2L, newest.frame.stamp.sequence)
        assertTrue(recovered.diagnoses.all { it.severity == Severity.OK })
        assertIs<SamplingDecision.Waiting>(sampler.completeAnalysis().single())
    }

    private fun accept(stamp: FrameStamp): Boolean = stamp.capture.sessionId == SessionId("pipeline")

    private fun frame(capture: CaptureId, sequence: Long, clock: PipelineClock): Frame = Frame(
        stamp = FrameStamp(capture, sequence, clock.now()),
        width = 720,
        height = 1_280,
        format = FrameFormat.JPEG,
        origin = FrameOrigin.MOCK,
        bytes = byteArrayOf(),
    )
}

private class PipelineClock : OliviaClock {
    private var monotonicMs: Long = 0

    override fun now(): ClockReading = ClockReading(monotonicMs, 1_700_000_000_000 + monotonicMs)

    fun advanceBy(durationMs: Long) {
        monotonicMs += durationMs
    }
}
