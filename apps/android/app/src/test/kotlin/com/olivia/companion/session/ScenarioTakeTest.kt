package com.olivia.companion.session

import com.olivia.core.CorrectionStatus
import com.olivia.core.DiagnosisKind
import com.olivia.core.TimelineEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScenarioTakeTest {
    @Test
    fun sampleCoversFramingLightHorizonPauseReconnectAndCuts() {
        val events = ScenarioTake.events()
        val types = events.map { it.type }.toSet()
        assertTrue(types.contains(TimelineEventType.SESSION_PAUSED))
        assertTrue(types.contains(TimelineEventType.SESSION_DEGRADED))
        assertTrue(types.contains(TimelineEventType.SESSION_RECOVERED))
        assertTrue(types.contains(TimelineEventType.CUT_SUGGESTED))

        val kinds = events.mapNotNull { it.diagnosis?.kind }.toSet()
        assertEquals(
            setOf(DiagnosisKind.FRAMING, DiagnosisKind.LIGHT, DiagnosisKind.HORIZON),
            kinds,
        )

        assertTrue(events.any { it.correctionStatus == CorrectionStatus.OBEYED })
        assertTrue(events.any { it.correctionStatus == CorrectionStatus.IGNORED })
        assertTrue(events.any { confirmationLabel(it) == "Confirmado" })
        assertTrue(events.any { confirmationLabel(it) == "Não confirmado" })
    }

    @Test
    fun reviewCountsCorrectionsAndCuts() {
        val take = takeFromEvents(
            sessionId = ScenarioTake.sessionId,
            title = "Cenário offline",
            events = ScenarioTake.events(),
            review = ScenarioTake.review(),
        )
        assertEquals(60_000, take.durationMs)
        assertEquals(3, take.correctionCount)
        assertEquals(3, take.cutCount)
        assertEquals("00:12", formatOffset(12_000))
        assertEquals("01:00", formatOffset(60_000))
    }
}
