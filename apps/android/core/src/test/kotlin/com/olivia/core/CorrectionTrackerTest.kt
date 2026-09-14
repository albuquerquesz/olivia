package com.olivia.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CorrectionTrackerTest {
    @Test
    fun `only a later clean frame confirms an audible correction`() {
        val tracker = CorrectionTracker()
        val correction = correction()
        assertTrue(tracker.begin(correction, AudioReceipt(correction.id, correction.capture, ClockReading(10, 10))))

        assertNull(tracker.onAnalysis(analysis(correction.capture, 10, Severity.OK, 0.9f), ClockReading(10, 10)))
        val outcome = tracker.onAnalysis(analysis(correction.capture, 11, Severity.OK, 0.9f), ClockReading(11, 11))
        assertEquals(CorrectionStatus.OBEYED, outcome?.status)
    }

    @Test
    fun `reliable persistent deviation becomes ignored only after its window`() {
        val tracker = CorrectionTracker()
        val correction = correction()
        tracker.begin(correction, AudioReceipt(correction.id, correction.capture, ClockReading(0, 0)))

        assertNull(tracker.onAnalysis(analysis(correction.capture, 1, Severity.MAJOR, 0.9f), ClockReading(7_999, 7_999)))
        val outcome = tracker.onAnalysis(analysis(correction.capture, 2, Severity.MAJOR, 0.9f), ClockReading(8_000, 8_000))
        assertEquals(CorrectionStatus.IGNORED, outcome?.status)
    }

    private fun correction(): Correction = Correction(
        id = "c1",
        capture = CaptureId(SessionId("take"), 1),
        issuedAt = ClockReading(0, 0),
        diagnosis = VisualDiagnosis(DiagnosisKind.FRAMING, Severity.MAJOR, Confidence(0.9f), Direction.TURN_RIGHT),
        direction = Direction.TURN_RIGHT,
        confidence = Confidence(0.9f),
        spokenText = "Vire para a direita.",
    )

    private fun analysis(capture: CaptureId, receivedAt: Long, severity: Severity, confidence: Float): FrameAnalysis = FrameAnalysis(
        frameStamp = FrameStamp(capture, receivedAt, ClockReading(receivedAt, receivedAt)),
        analyzedAt = ClockReading(receivedAt, receivedAt),
        latencyMs = 0,
        source = AnalyzerSource.MOCK,
        diagnoses = listOf(VisualDiagnosis(DiagnosisKind.FRAMING, severity, Confidence(confidence), if (severity == Severity.OK) null else Direction.TURN_RIGHT)),
    )
}
