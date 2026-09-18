package com.olivia.companion.session

import com.olivia.core.Confidence
import com.olivia.core.CorrectionStatus
import com.olivia.core.CutReason
import com.olivia.core.Direction
import com.olivia.core.DiagnosisKind
import com.olivia.core.SuggestedCut
import com.olivia.core.SessionId
import com.olivia.core.SessionReview
import com.olivia.core.Severity
import com.olivia.core.TimelineEvent
import com.olivia.core.TimelineEventType
import com.olivia.core.VisualDiagnosis

private data class SeedCorrection(
    val offsetMs: Long,
    val kind: DiagnosisKind,
    val direction: Direction,
    val label: String,
    val magnitude: Double,
)

internal fun seedTakes(nowMs: Long = System.currentTimeMillis()): List<TakeSummary> = listOf(
    seededTake(
        id = "seed-take-1",
        title = "Take tatuagem antebraço",
        startedAtEpochMs = nowMs - 35 * 60 * 1_000,
        durationMs = 32_000,
        corrections = listOf(
            SeedCorrection(8_000, DiagnosisKind.FRAMING, Direction.TURN_RIGHT, "Vire à direita", 20.0),
            SeedCorrection(22_000, DiagnosisKind.LIGHT, Direction.LEAVE_THE_SHADOW, "Saia da sombra", 1.0),
        ),
        cuts = listOf(
            SuggestedCut(8_000, 10_000, CutReason.UNCORRECTED_DEVIATION, Confidence(0.91f)),
        ),
    ),
    seededTake(
        id = "seed-take-2",
        title = "Take detalhe do desenho",
        startedAtEpochMs = nowMs - 2 * 60 * 60 * 1_000,
        durationMs = 27_000,
        corrections = listOf(
            SeedCorrection(11_000, DiagnosisKind.HORIZON, Direction.LEVEL_HORIZON_LEFT, "Nivele o horizonte", 8.0),
        ),
        cuts = listOf(
            SuggestedCut(11_000, 13_000, CutReason.SPEECH_RETAKE, Confidence(0.88f)),
        ),
    ),
    seededTake(
        id = "seed-take-3",
        title = "Take abertura do estúdio",
        startedAtEpochMs = nowMs - 26 * 60 * 60 * 1_000,
        durationMs = 41_000,
        corrections = listOf(
            SeedCorrection(7_000, DiagnosisKind.FRAMING, Direction.MOVE_CLOSER, "Aproxime um pouco", 12.0),
            SeedCorrection(25_000, DiagnosisKind.LIGHT, Direction.FACE_THE_LIGHT, "Vire para a luz", 1.0),
            SeedCorrection(34_000, DiagnosisKind.HORIZON, Direction.LEVEL_HORIZON_RIGHT, "Nivele o horizonte", 7.0),
        ),
        cuts = listOf(
            SuggestedCut(7_000, 9_000, CutReason.UNCORRECTED_DEVIATION, Confidence(0.86f)),
            SuggestedCut(25_000, 27_000, CutReason.SILENCE, Confidence(0.82f)),
        ),
    ),
)

private fun seededTake(
    id: String,
    title: String,
    startedAtEpochMs: Long,
    durationMs: Long,
    corrections: List<SeedCorrection>,
    cuts: List<SuggestedCut>,
): TakeSummary {
    val sessionId = SessionId(id)
    val confidence = Confidence(0.94f)
    val events = buildList {
        add(
            TimelineEvent(
                id = "$id-started",
                sessionId = sessionId,
                atEpochMs = startedAtEpochMs,
                offsetMs = 0,
                type = TimelineEventType.SESSION_STARTED,
                label = "Sessão iniciada",
            ),
        )
        corrections.forEachIndexed { index, correction ->
            val correctionId = "$id-correction-${index + 1}"
            val diagnosis = VisualDiagnosis(
                kind = correction.kind,
                severity = Severity.MAJOR,
                confidence = confidence,
                direction = correction.direction,
                magnitude = correction.magnitude,
            )
            add(
                TimelineEvent(
                    id = "$correctionId-diagnosis",
                    sessionId = sessionId,
                    atEpochMs = startedAtEpochMs + correction.offsetMs,
                    offsetMs = correction.offsetMs,
                    type = TimelineEventType.DIAGNOSIS,
                    diagnosis = diagnosis,
                    direction = correction.direction,
                    confidence = confidence,
                    label = correction.kind.label(),
                ),
            )
            add(
                TimelineEvent(
                    id = "$correctionId-issued",
                    sessionId = sessionId,
                    atEpochMs = startedAtEpochMs + correction.offsetMs,
                    offsetMs = correction.offsetMs,
                    type = TimelineEventType.CORRECTION_ISSUED,
                    diagnosis = diagnosis,
                    direction = correction.direction,
                    confidence = confidence,
                    correctionStatus = CorrectionStatus.PENDING,
                    correctionId = correctionId,
                    label = correction.label,
                ),
            )
            add(
                TimelineEvent(
                    id = "$correctionId-resolved",
                    sessionId = sessionId,
                    atEpochMs = startedAtEpochMs + correction.offsetMs + 2_000,
                    offsetMs = correction.offsetMs + 2_000,
                    type = TimelineEventType.CORRECTION_RESOLVED,
                    diagnosis = diagnosis,
                    direction = correction.direction,
                    confidence = confidence,
                    correctionStatus = CorrectionStatus.OBEYED,
                    correctionId = correctionId,
                    label = "Correção confirmada",
                ),
            )
        }
        cuts.forEachIndexed { index, cut ->
            add(
                TimelineEvent(
                    id = "$id-cut-${index + 1}",
                    sessionId = sessionId,
                    atEpochMs = startedAtEpochMs + cut.endMs,
                    offsetMs = cut.startMs,
                    type = TimelineEventType.CUT_SUGGESTED,
                    confidence = cut.confidence,
                    label = "Corte sugerido",
                ),
            )
        }
        add(
            TimelineEvent(
                id = "$id-stopped",
                sessionId = sessionId,
                atEpochMs = startedAtEpochMs + durationMs,
                offsetMs = durationMs,
                type = TimelineEventType.SESSION_STOPPED,
                label = "Sessão encerrada",
            ),
        )
    }
    val review = SessionReview(
        sessionId = sessionId,
        recording = null,
        transcript = emptyList(),
        suggestedCuts = cuts,
        timeline = events,
    )
    return takeFromEvents(sessionId, title, events, review)
}

private fun DiagnosisKind.label(): String = when (this) {
    DiagnosisKind.FRAMING -> "Enquadramento"
    DiagnosisKind.LIGHT -> "Luz e sombra"
    DiagnosisKind.HORIZON -> "Horizonte"
}
