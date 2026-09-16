package com.olivia.companion.session

import com.olivia.core.Confidence
import com.olivia.core.CorrectionStatus
import com.olivia.core.CutReason
import com.olivia.core.PostProcessor
import com.olivia.core.RecordingAsset
import com.olivia.core.SessionId
import com.olivia.core.SessionReview
import com.olivia.core.SuggestedCut
import com.olivia.core.TimelineEvent
import com.olivia.core.TimelineEventType

/**
 * Pós-processamento local das marcas já seguras da timeline.
 *
 * O vídeo nativo e a transcrição entram como adapters depois. Enquanto isso, as marcas de
 * correção não confirmada e os intervalos sem frame já produzem uma revisão consumível.
 */
class TimelinePostProcessor : PostProcessor {
    override suspend fun process(
        sessionId: SessionId,
        recording: RecordingAsset?,
        timeline: List<TimelineEvent>,
    ): Result<SessionReview> {
        val ordered = timeline
            .filter { it.type != TimelineEventType.CUT_SUGGESTED }
            .sortedBy { it.offsetMs }
        val cuts = unresolvedCorrections(ordered) + degradedIntervals(ordered)
        val cutEvents = cuts.mapIndexed { index, cut -> cutEvent(sessionId, ordered, cut, index) }
        val reviewTimeline = (ordered + cutEvents).sortedBy { it.offsetMs }
        return Result.success(
            SessionReview(
                sessionId = sessionId,
                recording = recording,
                transcript = emptyList(),
                suggestedCuts = cuts,
                timeline = reviewTimeline,
            ),
        )
    }

    private fun unresolvedCorrections(events: List<TimelineEvent>): List<SuggestedCut> = events
        .filter {
            it.type == TimelineEventType.CORRECTION_RESOLVED &&
                it.correctionStatus.isUnresolved()
        }
        .map { event ->
            SuggestedCut(
                startMs = (event.offsetMs - CORRECTION_WINDOW_MS).coerceAtLeast(0),
                endMs = event.offsetMs,
                reason = CutReason.UNCORRECTED_DEVIATION,
                confidence = event.confidence ?: DEFAULT_CONFIDENCE,
            )
        }

    private fun degradedIntervals(events: List<TimelineEvent>): List<SuggestedCut> = events
        .filter { it.type == TimelineEventType.SESSION_DEGRADED }
        .mapNotNull { degraded ->
            val recovered = events.firstOrNull {
                it.type == TimelineEventType.SESSION_RECOVERED &&
                    it.offsetMs > degraded.offsetMs
            }
            val stopped = events.firstOrNull {
                it.type == TimelineEventType.SESSION_STOPPED &&
                    it.offsetMs > degraded.offsetMs
            }
            val endMs = recovered?.offsetMs ?: stopped?.offsetMs ?: return@mapNotNull null
            if (endMs <= degraded.offsetMs) return@mapNotNull null
            SuggestedCut(
                startMs = degraded.offsetMs,
                endMs = endMs,
                reason = CutReason.DEGRADED_STRETCH,
                confidence = DEFAULT_CONFIDENCE,
            )
        }

    private fun cutEvent(
        sessionId: SessionId,
        events: List<TimelineEvent>,
        cut: SuggestedCut,
        index: Int,
    ): TimelineEvent {
        val atEpochMs = events.firstOrNull { it.offsetMs == cut.endMs }?.atEpochMs
            ?: events.firstOrNull()?.atEpochMs?.plus(cut.endMs)
            ?: 0L
        return TimelineEvent(
            id = "${sessionId.value}-cut-${cut.startMs}-${cut.endMs}-$index",
            sessionId = sessionId,
            atEpochMs = atEpochMs,
            offsetMs = cut.startMs,
            type = TimelineEventType.CUT_SUGGESTED,
            confidence = cut.confidence,
            label = cutLabel(cut.reason),
        )
    }

    private fun cutLabel(reason: CutReason): String = when (reason) {
        CutReason.UNCORRECTED_DEVIATION -> "Corte · correção não confirmada"
        CutReason.DEGRADED_STRETCH -> "Corte · trecho sem frame"
        CutReason.SPEECH_RETAKE -> "Corte · repetição na fala"
        CutReason.SILENCE -> "Corte · silêncio"
    }

    private companion object {
        val DEFAULT_CONFIDENCE = Confidence(0.75f)
        const val CORRECTION_WINDOW_MS = 2_000L
    }
}

private fun CorrectionStatus?.isUnresolved(): Boolean =
    this == CorrectionStatus.IGNORED ||
        this == CorrectionStatus.EXPIRED ||
        this == CorrectionStatus.SUPERSEDED
