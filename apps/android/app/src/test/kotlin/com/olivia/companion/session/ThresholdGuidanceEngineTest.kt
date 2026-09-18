package com.olivia.companion.session

import com.olivia.core.*
import org.junit.Assert.*
import org.junit.Test

class ThresholdGuidanceEngineTest {
    @Test fun sharedPolicyRequiresConsecutiveFramesAndHonorsCooldown() {
        val engine = ThresholdGuidanceEngine(4_000)
        assertNull(engine.onAnalysis(analysis(10_000), time(10_000)))
        val correction = engine.onAnalysis(analysis(12_000), time(12_000))!!
        assertFalse(correction.spokenText.contains("20"))
        assertFalse(correction.spokenText.contains("graus"))
        assertNull(engine.onAnalysis(analysis(14_000), time(14_000)))
        engine.onCorrectionOutcome(correction.id, CorrectionStatus.OBEYED)
        assertNull(engine.onAnalysis(analysis(15_000), time(15_000)))
        assertNull(engine.onAnalysis(analysis(16_000), time(16_000)))
        assertNotNull(engine.onAnalysis(analysis(18_000), time(18_000)))
    }

    @Test fun lowConfidenceAndHealthyFramesDoNotSpeak() {
        val engine = ThresholdGuidanceEngine(4_000)
        assertNull(engine.onAnalysis(analysis(10_000, 0.69f), time(10_000)))
        assertNull(engine.onAnalysis(analysis(12_000, 0.69f), time(12_000)))
        assertNull(engine.onAnalysis(analysis(14_000).copy(diagnoses = emptyList()), time(14_000)))
    }

    private fun time(ms: Long) = ClockReading(ms, ms)
    private fun analysis(ms: Long, confidence: Float = 0.91f) = FrameAnalysis(
        FrameStamp(CaptureId(SessionId("guidance-test"), 1), ms, time(ms)), time(ms), 0,
        AnalyzerSource.MOCK, listOf(VisualDiagnosis(DiagnosisKind.FRAMING, Severity.MAJOR,
            Confidence(confidence), Direction.TURN_RIGHT)),
    )
}
