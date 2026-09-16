package com.olivia.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CaptureFenceTest {
    @Test
    fun `late result cannot cross stop or a new session`() {
        val fence = CaptureFence()
        val stamp = FrameStamp(fence.open(SessionId("first")), 0, ClockReading(0, 100))
        assertTrue(fence.accepts(stamp, SessionState.STARTED))
        fence.invalidate()
        assertFalse(fence.accepts(stamp, SessionState.STARTED))
        fence.open(SessionId("second"))
        assertFalse(fence.accepts(stamp, SessionState.STARTED))
    }

    @Test
    fun `resume in same session rejects the previous generation`() {
        val fence = CaptureFence()
        val session = SessionId("take")
        val old = FrameStamp(fence.open(session), 0, ClockReading(0, 100))
        fence.invalidate()
        val fresh = FrameStamp(fence.open(session), 0, ClockReading(5_000, 90))
        assertFalse(fence.accepts(old, SessionState.STARTED))
        assertTrue(fence.accepts(fresh, SessionState.STARTED))
    }

    @Test
    fun `valid generation stays available only while started or degraded`() {
        val fence = CaptureFence()
        val stamp = FrameStamp(fence.open(SessionId("take")), 0, ClockReading(0, 100))
        SessionState.entries.filter { it !in setOf(SessionState.STARTED, SessionState.DEGRADED) }.forEach {
            assertFalse(fence.accepts(stamp, it), "state=$it")
        }
        assertTrue(fence.accepts(stamp, SessionState.DEGRADED))
    }
}
