package com.olivia.companion.session

import com.olivia.core.Confidence
import com.olivia.core.Correction
import com.olivia.core.CorrectionStatus
import com.olivia.core.Direction
import com.olivia.core.FrameAnalysis
import com.olivia.core.GuidanceEngine
import com.olivia.core.Severity
import com.olivia.core.VisualDiagnosis
import java.util.Locale

/** Política determinística do demo: uma correção por janela de cooldown. */
internal class MockGuidanceEngine(
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
            spokenText = spokenText(direction, diagnosis.magnitude),
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

    private fun spokenText(direction: Direction, magnitude: Double?): String {
        val degrees = magnitude?.let { String.format(Locale.US, "%.0f", it) } ?: "alguns"
        return when (direction) {
            Direction.TURN_LEFT -> "Vire uns $degrees graus à esquerda."
            Direction.TURN_RIGHT -> "Vire uns $degrees graus à direita."
            Direction.MOVE_CLOSER -> "Chegue um pouco mais perto da peça."
            Direction.MOVE_BACK -> "Afaste um pouco a câmera da peça."
            Direction.RAISE -> "Levante um pouco a câmera."
            Direction.LOWER -> "Abaixe um pouco a câmera."
            Direction.FACE_THE_LIGHT -> "Vire a peça para a luz."
            Direction.AVOID_BACKLIGHT -> "Evite a luz atrás da peça."
            Direction.LEAVE_THE_SHADOW -> "A sua mão está fazendo sombra na peça."
            Direction.LEVEL_HORIZON_LEFT -> "Endireite o horizonte à esquerda."
            Direction.LEVEL_HORIZON_RIGHT -> "Endireite o horizonte à direita."
        }
    }

    private companion object {
        const val MIN_CONFIDENCE = 0.7f
    }
}
