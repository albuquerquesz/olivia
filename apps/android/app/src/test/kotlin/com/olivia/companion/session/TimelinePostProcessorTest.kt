package com.olivia.companion.session

import com.olivia.core.CutReason
import com.olivia.core.TimelineEventType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelinePostProcessorTest {
    @Test
    fun reviewCreatesCutsFromUnresolvedCorrectionsAndDegradedIntervals() = runTest {
        val review = TimelinePostProcessor()
            .process(ScenarioTake.sessionId, null, ScenarioTake.events())
            .getOrThrow()

        assertEquals(2, review.suggestedCuts.size)
        assertTrue(review.suggestedCuts.any { it.reason == CutReason.UNCORRECTED_DEVIATION })
        assertTrue(review.suggestedCuts.any { it.reason == CutReason.DEGRADED_STRETCH })
        assertEquals(
            2,
            review.timeline.count { it.type == TimelineEventType.CUT_SUGGESTED },
        )
    }
}
