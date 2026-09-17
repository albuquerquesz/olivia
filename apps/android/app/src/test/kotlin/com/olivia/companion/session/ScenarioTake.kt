package com.olivia.companion.session

import com.olivia.core.Confidence
import com.olivia.core.CorrectionStatus
import com.olivia.core.CutReason
import com.olivia.core.DiagnosisKind
import com.olivia.core.Direction
import com.olivia.core.RecordingAsset
import com.olivia.core.SessionId
import com.olivia.core.SessionReview
import com.olivia.core.Severity
import com.olivia.core.SuggestedCut
import com.olivia.core.TimelineEvent
import com.olivia.core.TimelineEventType
import com.olivia.core.VisualDiagnosis

object ScenarioTake {
    val sessionId: SessionId = SessionId("scenario-ensaio")
    const val STARTED_AT_EPOCH_MS: Long = 1_725_500_000_000L

    fun events(): List<TimelineEvent> {
        val id = sessionId
        val start = STARTED_AT_EPOCH_MS
        val framing = VisualDiagnosis(
            kind = DiagnosisKind.FRAMING,
            severity = Severity.MAJOR,
            confidence = Confidence(0.91f),
            direction = Direction.TURN_RIGHT,
            magnitude = 20.0,
        )
        val light = VisualDiagnosis(
            kind = DiagnosisKind.LIGHT,
            severity = Severity.MAJOR,
            confidence = Confidence(0.88f),
            direction = Direction.LEAVE_THE_SHADOW,
        )
        val horizon = VisualDiagnosis(
            kind = DiagnosisKind.HORIZON,
            severity = Severity.MINOR,
            confidence = Confidence(0.74f),
            direction = Direction.LEVEL_HORIZON_LEFT,
            magnitude = 8.0,
        )
        return listOf(
            event(id, start, 0, TimelineEventType.SESSION_STARTED, "Sessão iniciada"),
            event(
                id, start, 12_000, TimelineEventType.CORRECTION_ISSUED,
                "Vire uns 20 graus à direita",
                diagnosis = framing,
                direction = Direction.TURN_RIGHT,
                status = CorrectionStatus.PENDING,
                correctionId = "c-frame",
            ),
            event(
                id, start, 14_000, TimelineEventType.CORRECTION_RESOLVED,
                "00:14 · recentrou",
                diagnosis = framing,
                direction = Direction.TURN_RIGHT,
                status = CorrectionStatus.OBEYED,
                correctionId = "c-frame",
            ),
            event(id, start, 20_000, TimelineEventType.SESSION_PAUSED, "Pausa"),
            event(id, start, 25_000, TimelineEventType.SESSION_RESUMED, "Retomada"),
            event(
                id, start, 31_000, TimelineEventType.CORRECTION_ISSUED,
                "A sua mão está fazendo sombra na peça",
                diagnosis = light,
                direction = Direction.LEAVE_THE_SHADOW,
                status = CorrectionStatus.PENDING,
                correctionId = "c-light",
            ),
            event(
                id, start, 34_000, TimelineEventType.CORRECTION_RESOLVED,
                "00:34 · saiu da sombra",
                diagnosis = light,
                direction = Direction.LEAVE_THE_SHADOW,
                status = CorrectionStatus.OBEYED,
                correctionId = "c-light",
            ),
            event(
                id, start, 40_000, TimelineEventType.CORRECTION_ISSUED,
                "Endireite o horizonte à esquerda",
                diagnosis = horizon,
                direction = Direction.LEVEL_HORIZON_LEFT,
                status = CorrectionStatus.PENDING,
                correctionId = "c-horizon",
            ),
            event(
                id, start, 42_000, TimelineEventType.CORRECTION_RESOLVED,
                "Horizonte ainda torto",
                diagnosis = horizon,
                direction = Direction.LEVEL_HORIZON_LEFT,
                status = CorrectionStatus.IGNORED,
                correctionId = "c-horizon",
            ),
            event(id, start, 50_000, TimelineEventType.SESSION_DEGRADED, "Queda de conexão"),
            event(id, start, 54_000, TimelineEventType.SESSION_RECOVERED, "Conexão restaurada"),
            event(id, start, 60_000, TimelineEventType.SESSION_STOPPED, "Sessão encerrada"),
            event(
                id, start, 12_000, TimelineEventType.CUT_SUGGESTED,
                "Corte · recentrou",
                status = CorrectionStatus.OBEYED,
                correctionId = "c-frame",
            ),
            event(
                id, start, 40_000, TimelineEventType.CUT_SUGGESTED,
                "Corte · horizonte não confirmado",
                status = CorrectionStatus.IGNORED,
                correctionId = "c-horizon",
            ),
            event(
                id, start, 50_000, TimelineEventType.CUT_SUGGESTED,
                "Corte · trecho sem frame",
            ),
        )
    }

    fun review(): SessionReview {
        val timeline = events()
        return SessionReview(
            sessionId = sessionId,
            recording = RecordingAsset(
                id = "scenario-recording",
                sessionId = sessionId,
                startedAtEpochMs = STARTED_AT_EPOCH_MS,
                durationMs = 60_000,
                sizeBytes = 12_000_000,
                hasAudioTrack = true,
            ),
            transcript = emptyList(),
            suggestedCuts = listOf(
                SuggestedCut(12_000, 14_000, CutReason.UNCORRECTED_DEVIATION, Confidence(0.8f)),
                SuggestedCut(40_000, 42_000, CutReason.UNCORRECTED_DEVIATION, Confidence(0.7f)),
                SuggestedCut(50_000, 54_000, CutReason.DEGRADED_STRETCH, Confidence(0.9f)),
            ),
            timeline = timeline,
        )
    }
}

private fun event(
    sessionId: SessionId,
    startedAt: Long,
    offsetMs: Long,
    type: TimelineEventType,
    label: String,
    diagnosis: VisualDiagnosis? = null,
    direction: Direction? = null,
    status: CorrectionStatus? = null,
    correctionId: String? = null,
): TimelineEvent = TimelineEvent(
    id = "${sessionId.value}-$type-$offsetMs-${label.hashCode()}",
    sessionId = sessionId,
    atEpochMs = startedAt + offsetMs,
    offsetMs = offsetMs,
    type = type,
    diagnosis = diagnosis,
    direction = direction,
    confidence = diagnosis?.confidence,
    correctionStatus = status,
    correctionId = correctionId,
    label = label,
)
