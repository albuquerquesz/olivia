package com.olivia.companion.session

import com.olivia.core.SessionId
import com.olivia.core.SessionReview
import com.olivia.core.TimelineEvent
import com.olivia.core.TimelineEventType

data class TakeSummary(
    val sessionId: SessionId,
    val title: String,
    val durationMs: Long,
    val recordedAtEpochMs: Long,
    val correctionCount: Int,
    val cutCount: Int,
    val events: List<TimelineEvent>,
    val review: SessionReview,
)

fun takeFromEvents(
    sessionId: SessionId,
    title: String,
    events: List<TimelineEvent>,
    review: SessionReview? = null,
): TakeSummary {
    val ordered = events.sortedBy { it.offsetMs }
    val durationMs = ordered.maxOfOrNull { it.offsetMs } ?: 0
    val recordedAtEpochMs = ordered.minOfOrNull { it.atEpochMs } ?: 0
    val correctionCount = ordered.count { it.type == TimelineEventType.CORRECTION_ISSUED }
    val cutCount = ordered.count { it.type == TimelineEventType.CUT_SUGGESTED }
    return TakeSummary(
        sessionId = sessionId,
        title = title,
        durationMs = durationMs,
        recordedAtEpochMs = recordedAtEpochMs,
        correctionCount = correctionCount,
        cutCount = cutCount,
        events = ordered,
        review = review ?: SessionReview(
            sessionId = sessionId,
            recording = null,
            transcript = emptyList(),
            suggestedCuts = emptyList(),
            timeline = ordered,
        ),
    )
}
