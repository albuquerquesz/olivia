package com.olivia.core

import java.text.Normalizer

/** A timeline event whose offset was independently aligned to native-media time by BE-12. */
public data class MediaTimelineEvent(val event: TimelineEvent, val mediaOffsetMs: Long) {
    init { require(mediaOffsetMs >= 0) }
}

/**
 * Deterministic, post-take-only cut planner. It suggests EXCLUSION intervals and never edits or
 * exports video. Timeline offsets are accepted only after the caller supplies a media alignment.
 */
public class CutPlanner(
    private val maximumCutMs: Long = 10_000,
) {
    init { require(maximumCutMs > 0) }

    public fun plan(
        durationMs: Long,
        transcript: List<TranscriptSegment>,
        timeline: List<MediaTimelineEvent>,
    ): List<SuggestedCut> {
        require(durationMs > 0)
        validateTranscript(durationMs, transcript)
        val cuts = mutableListOf<CandidateCut>()
        cuts += uncorrectedDeviationCuts(durationMs, timeline)
        cuts += degradedCuts(durationMs, timeline)
        cuts += retakeCuts(transcript)
        return mergeAndClamp(durationMs, cuts)
    }

    private fun uncorrectedDeviationCuts(durationMs: Long, timeline: List<MediaTimelineEvent>): List<CandidateCut> {
        val issued = timeline.filter { it.event.type == TimelineEventType.CORRECTION_ISSUED && it.event.correctionId != null }
        val outcomes = timeline.filter { it.event.type == TimelineEventType.CORRECTION_RESOLVED && it.event.correctionId != null }
            .associateBy { it.event.correctionId }
        return issued.mapNotNull { issue ->
            val outcome = outcomes[issue.event.correctionId]
            if (outcome?.event?.correctionStatus == CorrectionStatus.OBEYED) return@mapNotNull null
            val end = outcome?.mediaOffsetMs ?: (issue.mediaOffsetMs + maximumCutMs).coerceAtMost(durationMs)
            candidate(issue.mediaOffsetMs, end, CutReason.UNCORRECTED_DEVIATION, Confidence(0.85f))
        }
    }

    private fun degradedCuts(durationMs: Long, timeline: List<MediaTimelineEvent>): List<CandidateCut> {
        val ordered = timeline.sortedBy(MediaTimelineEvent::mediaOffsetMs)
        return ordered.mapIndexedNotNull { index, marker ->
            if (marker.event.type != TimelineEventType.SESSION_DEGRADED) return@mapIndexedNotNull null
            val recovery = ordered.drop(index + 1).firstOrNull {
                it.event.type in setOf(TimelineEventType.SESSION_RECOVERED, TimelineEventType.SESSION_STOPPED)
            }
            candidate(marker.mediaOffsetMs, recovery?.mediaOffsetMs ?: durationMs, CutReason.DEGRADED_STRETCH, Confidence(0.9f))
        }
    }

    private fun retakeCuts(transcript: List<TranscriptSegment>): List<CandidateCut> = transcript.mapNotNull { segment ->
        if (retakeWords.any { normalize(segment.text).contains(it) }) {
            candidate(segment.startMs, segment.endMs, CutReason.SPEECH_RETAKE, Confidence(0.75f))
        } else null
    }

    private fun mergeAndClamp(durationMs: Long, candidates: List<CandidateCut>): List<SuggestedCut> {
        val clamped = candidates.mapNotNull { candidate ->
            val start = candidate.startMs.coerceIn(0, durationMs)
            val end = candidate.endMs.coerceIn(0, durationMs).coerceAtMost(start + maximumCutMs)
            // A full-session suggestion is not actionable evidence of unusable material.
            if (end <= start || (start == 0L && end == durationMs)) null else candidate.copy(startMs = start, endMs = end)
        }.sortedBy(CandidateCut::startMs)
        return clamped.fold(mutableListOf()) { merged, next ->
            val previous = merged.lastOrNull()
            if (previous != null && next.startMs <= previous.endMs) {
                val primary = if (priority(next.reason) > priority(previous.reason)) next else previous
                merged[merged.lastIndex] = primary.copy(
                    startMs = minOf(previous.startMs, next.startMs),
                    endMs = maxOf(previous.endMs, next.endMs).coerceAtMost(minOf(previous.startMs, next.startMs) + maximumCutMs),
                )
            } else merged += next
            merged
        }.map { SuggestedCut(it.startMs, it.endMs, it.reason, it.confidence) }
    }

    private fun candidate(startMs: Long, endMs: Long, reason: CutReason, confidence: Confidence): CandidateCut? =
        CandidateCut(startMs, endMs, reason, confidence).takeIf { endMs > startMs }

    private fun validateTranscript(durationMs: Long, transcript: List<TranscriptSegment>) {
        transcript.fold(0L) { previousEnd, segment ->
            require(segment.startMs >= previousEnd && segment.endMs > segment.startMs && segment.endMs <= durationMs)
            segment.endMs
        }
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace("\\p{M}".toRegex(), "").lowercase()

    private fun priority(reason: CutReason): Int = when (reason) {
        CutReason.UNCORRECTED_DEVIATION -> 3
        CutReason.DEGRADED_STRETCH -> 2
        CutReason.SPEECH_RETAKE -> 1
        CutReason.SILENCE -> 0
    }

    private data class CandidateCut(val startMs: Long, val endMs: Long, val reason: CutReason, val confidence: Confidence)

    private companion object {
        val retakeWords = setOf("refazer", "de novo", "recomecar", "repete")
    }
}
