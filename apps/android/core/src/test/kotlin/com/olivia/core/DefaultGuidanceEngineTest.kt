package com.olivia.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultGuidanceEngineTest {
    @Test
    fun `prioritizes severity confidence then framing and enforces cooldown`() {
        val engine = DefaultGuidanceEngine(AudioMode.VOICE)
        val clock = GuidanceClock()
        val analysis = analysis(
            listOf(
                diagnosis(DiagnosisKind.LIGHT, Direction.LEAVE_THE_SHADOW, 0.9f),
                diagnosis(DiagnosisKind.FRAMING, Direction.TURN_RIGHT, 0.9f),
            ),
        )

        assertNull(engine.onAnalysis(analysis(sequence = 1), clock.now()))
        val correction = engine.onAnalysis(analysis(sequence = 2), clock.now())
        assertEquals(Direction.TURN_RIGHT, correction?.direction)
        assertEquals("Vire para a direita.", correction?.spokenText)
        engine.onCorrectionOutcome(requireNotNull(correction).id, CorrectionStatus.OBEYED)
        clock.advanceBy(3_999)
        assertNull(engine.onAnalysis(analysis, clock.now()))
        clock.advanceBy(1)
        assertNull(engine.onAnalysis(analysis(sequence = 3), clock.now()))
        assertTrue(engine.onAnalysis(analysis(sequence = 4), clock.now()) != null)
    }

    @Test
    fun `does not speak a low confidence or non major diagnosis`() {
        val engine = DefaultGuidanceEngine(AudioMode.VOICE)
        val clock = GuidanceClock()
        assertNull(engine.onAnalysis(analysis(listOf(diagnosis(DiagnosisKind.FRAMING, Direction.TURN_RIGHT, 0.79f))), clock.now()))
        assertNull(
            engine.onAnalysis(
                analysis(listOf(diagnosis(DiagnosisKind.FRAMING, Direction.TURN_RIGHT, 0.9f, Severity.MINOR))),
                clock.now(),
            ),
        )
    }

    @Test
    fun `requires two matching actionable frames and resets on a different direction`() {
        val engine = DefaultGuidanceEngine(AudioMode.VOICE)
        val clock = GuidanceClock()
        val right = { sequence: Long -> analysis(listOf(diagnosis(DiagnosisKind.FRAMING, Direction.TURN_RIGHT, 0.9f)), sequence) }
        val left = { sequence: Long -> analysis(listOf(diagnosis(DiagnosisKind.FRAMING, Direction.TURN_LEFT, 0.9f)), sequence) }

        assertNull(engine.onAnalysis(right(1), clock.now()))
        assertNull(engine.onAnalysis(left(2), clock.now()))
        assertNull(engine.onAnalysis(right(3), clock.now()))
        assertEquals(Direction.TURN_RIGHT, engine.onAnalysis(right(4), clock.now())?.direction)
    }

    private fun analysis(diagnoses: List<VisualDiagnosis>, sequence: Long = 1): FrameAnalysis = FrameAnalysis(
        frameStamp = FrameStamp(CaptureId(SessionId("take"), 1), sequence, ClockReading(0, 1_700_000_000_000)),
        analyzedAt = ClockReading(0, 1_700_000_000_000),
        latencyMs = 0,
        source = AnalyzerSource.MOCK,
        diagnoses = diagnoses,
    )

    private fun diagnosis(kind: DiagnosisKind, direction: Direction, confidence: Float, severity: Severity = Severity.MAJOR) =
        VisualDiagnosis(kind, severity, Confidence(confidence), direction)
}

private class GuidanceClock : OliviaClock {
    private var monotonicMs: Long = 0
    override fun now(): ClockReading = ClockReading(monotonicMs, 1_700_000_000_000 + monotonicMs)
    fun advanceBy(value: Long) { monotonicMs += value }
}
