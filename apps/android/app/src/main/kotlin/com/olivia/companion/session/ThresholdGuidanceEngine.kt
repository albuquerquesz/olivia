package com.olivia.companion.session

import com.olivia.core.Confidence
import com.olivia.core.Correction
import com.olivia.core.CorrectionStatus
import com.olivia.core.Direction
import com.olivia.core.FrameAnalysis
import com.olivia.core.GuidanceEngine
import com.olivia.core.Severity
import com.olivia.core.VisualDiagnosis

/** Política de direção: uma correção por janela de cooldown. */
internal class ThresholdGuidanceEngine(
    private val correctionCooldownMs: Long,
) : GuidanceEngine {
    private var lastCorrectionAtEpochMs: Long? = null
    private val outcomes = mutableMapOf<String, CorrectionStatus>()

    override fun onAnalysis(analysis: FrameAnalysis, nowEpochMs: Long): Correction? {
        val diagnosis = analysis.diagnoses
            .filter { it.severity != Severity.OK && it.confidence.value >= MIN_CONFIDENCE }
            .maxWithOrNull(compareBy<VisualDiagnosis> { severityRank(it.severity) }.thenBy { it.confidence.value })
            ?: return null
        val direction = diagnosis.direction ?: return null
        val lastCorrectionAt = lastCorrectionAtEpochMs
        if (lastCorrectionAt != null && nowEpochMs - lastCorrectionAt < correctionCooldownMs) return null
        return Correction(
            id = "${analysis.sessionId.value}-${analysis.frameCapturedAtEpochMs}-${diagnosis.kind}",
            sessionId = analysis.sessionId,
            issuedAtEpochMs = nowEpochMs,
            diagnosis = diagnosis,
            direction = direction,
            confidence = diagnosis.confidence,
            spokenText = spokenTextFor(direction, diagnosis.magnitude),
        ).also {
            lastCorrectionAtEpochMs = nowEpochMs
        }
    }

    override fun onCorrectionOutcome(correctionId: String, status: CorrectionStatus) {
        outcomes[correctionId] = status
    }

    fun reset() {
        lastCorrectionAtEpochMs = null
        outcomes.clear()
    }

    private fun severityRank(severity: Severity): Int = when (severity) {
        Severity.OK -> 0
        Severity.MINOR -> 1
        Severity.MAJOR -> 2
    }

    private companion object {
        const val MIN_CONFIDENCE = 0.7f
    }
}
