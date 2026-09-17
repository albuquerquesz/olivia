package com.olivia.companion.session

import com.olivia.core.SessionConfig
import com.olivia.core.SessionId
import com.olivia.core.SessionState
import com.olivia.core.TimelineEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRuntimeTest {
    @Test
    fun startPauseResumeStopRecordsTimelineAndFreezesElapsed() {
        var now = 1_000_000L
        val timeline = InMemoryTimeline()
        val runtime = SessionRuntime(timeline) { now }

        val started = runtime.start(SessionConfig())
        assertEquals(SessionState.STARTED, started?.state)

        now += 5_000
        assertEquals(5_000, runtime.elapsed())

        runtime.pause()
        now += 3_000
        assertEquals(5_000, runtime.elapsed())
        assertEquals(SessionState.PAUSED, runtime.snapshot?.state)

        runtime.resume()
        now += 2_000
        assertEquals(7_000, runtime.elapsed())

        runtime.stop()
        now += 10_000
        assertEquals(7_000, runtime.elapsed())
        assertEquals(SessionState.STOPPED, runtime.snapshot?.state)

        val types = timeline.events(started!!.id).map { it.type }
        assertEquals(
            listOf(
                TimelineEventType.SESSION_STARTED,
                TimelineEventType.SESSION_PAUSED,
                TimelineEventType.SESSION_RESUMED,
                TimelineEventType.SESSION_STOPPED,
            ),
            types,
        )
    }

    @Test
    fun degradeAndRecoverAreDistinctFromPause() {
        var now = 2_000_000L
        val timeline = InMemoryTimeline()
        val runtime = SessionRuntime(timeline) { now }

        runtime.start(SessionConfig())
        runtime.degrade()
        assertEquals(SessionState.DEGRADED, runtime.snapshot?.state)
        assertNull(runtime.snapshot?.lastFrameAtEpochMs)

        now += 4_000
        assertEquals(4_000, runtime.elapsed())

        runtime.recover()
        assertEquals(SessionState.STARTED, runtime.snapshot?.state)

        runtime.pause()
        val ignored = runtime.degrade()
        assertEquals(SessionState.PAUSED, ignored?.state)
    }

    @Test
    fun frameAfterDegradedSessionRecordsRecovery() {
        var now = 3_000_000L
        val timeline = InMemoryTimeline()
        val runtime = SessionRuntime(timeline) { now }

        val started = runtime.start(SessionConfig())!!
        runtime.degrade()
        now += 4_000
        runtime.frameReceived(now)

        assertEquals(SessionState.STARTED, runtime.snapshot?.state)
        assertEquals(now, runtime.snapshot?.lastFrameAtEpochMs)
        assertEquals(
            listOf(
                TimelineEventType.SESSION_STARTED,
                TimelineEventType.SESSION_DEGRADED,
                TimelineEventType.SESSION_RECOVERED,
            ),
            timeline.events(started.id).map { it.type },
        )
    }

    @Test
    fun invalidStartWhileLiveIsIgnored() {
        val runtime = SessionRuntime(InMemoryTimeline()) { 0 }
        val first = runtime.start(SessionConfig())
        val second = runtime.start(SessionConfig())
        assertEquals(first?.id, second?.id)
        assertTrue(runtime.snapshot?.state == SessionState.STARTED)
    }

    @Test
    fun invalidPauseResumeDegradeRecoverAndStopDoNotCreateEvents() {
        var now = 4_000_000L
        val timeline = InMemoryTimeline()
        val runtime = SessionRuntime(timeline) { now }

        assertNull(runtime.pause())
        assertNull(runtime.resume())
        assertNull(runtime.degrade())
        assertNull(runtime.recover())
        assertNull(runtime.stop())

        val started = runtime.start(SessionConfig())!!
        assertEquals(SessionState.STARTED, runtime.resume()?.state)
        assertEquals(SessionState.STARTED, runtime.recover()?.state)
        assertEquals(SessionState.STOPPED, runtime.stop()?.state)

        now += 1_000
        assertEquals(SessionState.STOPPED, runtime.pause()?.state)
        assertEquals(
            listOf(TimelineEventType.SESSION_STARTED, TimelineEventType.SESSION_STOPPED),
            timeline.events(started.id).map { it.type },
        )
    }

    @Test
    fun delayedOrForeignFramesAreNotAcceptedAfterPauseOrStop() {
        var now = 5_000_000L
        val runtime = SessionRuntime(InMemoryTimeline()) { now }
        val started = runtime.start(SessionConfig())!!

        assertTrue(runtime.canProcessFrame(started.id, now))
        now += 2_000
        runtime.frameReceived(now)
        assertTrue(!runtime.canProcessFrame(started.id, now - 1))
        assertTrue(!runtime.canProcessFrame(SessionId("other"), now))

        runtime.pause()
        assertTrue(!runtime.canProcessFrame(started.id, now + 1))
        runtime.resume()
        runtime.stop()
        assertTrue(!runtime.canProcessFrame(started.id, now + 2_000))
        assertEquals(SessionState.STOPPED, runtime.snapshot?.state)
    }

    @Test
    fun watchdogDegradesOnlyAtTheConfiguredFourSecondWindowAndNeverWhilePaused() {
        var now = 6_000_000L
        val runtime = SessionRuntime(InMemoryTimeline()) { now }
        runtime.start(SessionConfig())

        now += 3_999
        assertTrue(!runtime.shouldDegrade())
        now += 1
        assertTrue(runtime.shouldDegrade())

        runtime.pause()
        now += 10_000
        assertTrue(!runtime.shouldDegrade())
    }
}
