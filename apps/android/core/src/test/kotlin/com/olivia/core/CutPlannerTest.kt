package com.olivia.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CutPlannerTest {
    @Test
    fun `cuts an ignored correction in aligned native media time`() {
        val cuts = CutPlanner().plan(
            durationMs = 30_000,
            transcript = emptyList(),
            timeline = listOf(
                marker(TimelineEventType.CORRECTION_ISSUED, 5_000, correctionId = "one"),
                marker(TimelineEventType.CORRECTION_RESOLVED, 8_000, correctionId = "one", status = CorrectionStatus.IGNORED),
            ),
        )

        assertEquals(listOf(SuggestedCut(5_000, 8_000, CutReason.UNCORRECTED_DEVIATION, Confidence(0.85f))), cuts)
    }

    @Test
    fun `joins overlapping causes and never infers silence from absent transcript`() {
        val cuts = CutPlanner().plan(
            durationMs = 30_000,
            transcript = listOf(TranscriptSegment(7_000, 8_000, "vamos refazer")),
            timeline = listOf(
                marker(TimelineEventType.SESSION_DEGRADED, 6_000),
                marker(TimelineEventType.SESSION_RECOVERED, 9_000),
            ),
        )

        assertEquals(1, cuts.size)
        assertEquals(CutReason.DEGRADED_STRETCH, cuts.single().reason)
        assertEquals(6_000, cuts.single().startMs)
        assertEquals(9_000, cuts.single().endMs)
        assertTrue(cuts.none { it.reason == CutReason.SILENCE })
    }

    private fun marker(
        type: TimelineEventType,
        mediaOffsetMs: Long,
        correctionId: String? = null,
        status: CorrectionStatus? = null,
    ): MediaTimelineEvent = MediaTimelineEvent(
        TimelineEvent(
            id = "$type:$mediaOffsetMs",
            sessionId = SessionId("review"),
            atEpochMs = mediaOffsetMs,
            offsetMs = mediaOffsetMs,
            type = type,
            correctionId = correctionId,
            correctionStatus = status,
        ),
        mediaOffsetMs,
    )
}
