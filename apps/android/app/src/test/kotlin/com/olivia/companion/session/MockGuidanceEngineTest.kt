package com.olivia.companion.session

import com.olivia.core.AnalyzerSource
import com.olivia.core.Confidence
import com.olivia.core.Direction
import com.olivia.core.FrameAnalysis
import com.olivia.core.DiagnosisKind
import com.olivia.core.SessionId
import com.olivia.core.Severity
import com.olivia.core.VisualDiagnosis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MockGuidanceEngineTest {
    @Test
    fun emitsShortCorrectionAndHonorsCooldown() {
        val engine = MockGuidanceEngine(correctionCooldownMs = 4_000L)
        val issue = analysis(
            VisualDiagnosis(
                kind = DiagnosisKind.FRAMING,
                severity = Severity.MAJOR,
                confidence = Confidence(0.91f),
                direction = Direction.TURN_RIGHT,
                magnitude = 20.0,
            ),
        )

        val correction = engine.onAnalysis(issue, nowEpochMs = 12_000L)

        assertNotNull(correction)
        assertEquals("Vire uns 20 graus à direita.", correction?.spokenText)
        assertNull(engine.onAnalysis(issue, nowEpochMs = 14_000L))
        assertNotNull(engine.onAnalysis(issue, nowEpochMs = 16_000L))
    }

    @Test
    fun ignoresLowConfidenceAndHealthyFrames() {
        val engine = MockGuidanceEngine(correctionCooldownMs = 4_000L)
        val lowConfidence = analysis(
            VisualDiagnosis(
                kind = DiagnosisKind.LIGHT,
                severity = Severity.MAJOR,
                confidence = Confidence(0.69f),
                direction = Direction.LEAVE_THE_SHADOW,
            ),
        )

        assertNull(engine.onAnalysis(lowConfidence, nowEpochMs = 12_000L))
        assertNull(engine.onAnalysis(analysis(), nowEpochMs = 14_000L))
    }

    private fun analysis(diagnosis: VisualDiagnosis? = null): FrameAnalysis = FrameAnalysis(
        sessionId = SessionId("guidance-test"),
        frameCapturedAtEpochMs = 12_000L,
        analyzedAtEpochMs = 12_000L,
        latencyMs = 0L,
        source = AnalyzerSource.MOCK,
        diagnoses = listOfNotNull(diagnosis),
    )
}
