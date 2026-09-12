package com.olivia.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContractPolicyTest {
    @Test
    fun `lower sampling never changes the capture watchdog`() {
        val defaults = SessionConfig()
        assertEquals(0.5, defaults.samplingFps)
        val slower = defaults.copy(analysisIntervalMs = 10_000)
        assertEquals(0.1, slower.samplingFps)
        assertEquals(4_000L, slower.degradedAfterMs)
        assertEquals(4_000L, slower.correctionCooldownMs)
    }

    @Test
    fun `invalid intervals are rejected`() {
        assertFailsWith<IllegalArgumentException> { SessionConfig(analysisIntervalMs = 0) }
        assertFailsWith<IllegalArgumentException> { SessionConfig(degradedAfterMs = 0) }
        assertFailsWith<IllegalArgumentException> { SessionConfig(correctionCooldownMs = -1) }
    }

    @Test
    fun `wall clock changes cannot change elapsed time`() {
        val start = ClockReading(100, 10_000)
        assertEquals(4_000L, ClockReading(4_100, 1).elapsedSince(start))
        assertEquals(4_000L, ClockReading(4_100, 999_999).elapsedSince(start))
        assertFailsWith<IllegalArgumentException> { ClockReading(99, 10_000).elapsedSince(start) }
    }

    @Test
    fun `processing requires stopped and cancellation returns to stopped`() {
        listOf(SessionState.STARTED, SessionState.PAUSED, SessionState.DEGRADED, SessionState.STOPPING)
            .forEach { assertFalse(SessionTransitions.allows(it, SessionState.PROCESSING)) }
        assertTrue(SessionTransitions.allows(SessionState.STOPPED, SessionState.PROCESSING))
        assertTrue(SessionTransitions.allows(SessionState.PROCESSING, SessionState.STOPPED))
        assertFalse(SessionTransitions.allows(SessionState.STARTED, SessionState.READY))
    }

    @Test
    fun `degradation keeps independent causes and paused intention`() {
        val snapshot = SessionSnapshot(
            SessionId("fixture"), SessionState.DEGRADED, 1_000, 4_000, SessionConfig(), 1_000,
            degradationCauses = setOf(DegradationCause.CAPTURE_UNAVAILABLE, DegradationCause.ANALYSIS_UNAVAILABLE),
            resumeState = SessionState.PAUSED,
        )
        assertEquals(SessionState.PAUSED, snapshot.resumeState)
        assertTrue(SessionTransitions.allows(snapshot.state, snapshot.resumeState))
        assertFailsWith<IllegalArgumentException> { snapshot.copy(state = SessionState.STARTED) }
        assertFailsWith<IllegalArgumentException> { snapshot.copy(degradationCauses = emptySet()) }
    }

    @Test
    fun `pause and connection recovery have distinct audio intents`() {
        assertTrue(SessionSignal.SESSION_PAUSED != SessionSignal.CONNECTION_LOST)
        assertTrue(SessionSignal.SESSION_RESUMED != SessionSignal.CONNECTION_RESTORED)
        assertTrue(SessionSignal.ANALYSIS_UNAVAILABLE != SessionSignal.CONNECTION_LOST)
    }

    @Test
    fun `invalid model values do not become diagnoses`() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, -0.1f, 1.1f).forEach {
            assertFailsWith<IllegalArgumentException> { Confidence(it) }
        }
        assertFailsWith<IllegalArgumentException> {
            VisualDiagnosis(DiagnosisKind.LIGHT, Severity.MAJOR, Confidence(0.9f), Direction.TURN_LEFT)
        }
        assertFailsWith<IllegalArgumentException> {
            VisualDiagnosis(DiagnosisKind.FRAMING, Severity.OK, Confidence(0.9f), Direction.TURN_LEFT)
        }
        assertFailsWith<IllegalArgumentException> {
            VisualDiagnosis(DiagnosisKind.FRAMING, Severity.MAJOR, Confidence(0.9f), Direction.TURN_LEFT, Double.NaN)
        }
    }
}
