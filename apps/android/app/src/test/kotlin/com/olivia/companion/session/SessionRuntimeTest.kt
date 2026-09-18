package com.olivia.companion.session

import com.olivia.core.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** The operational UI now shares the backend coordinator rather than a second state machine. */
class SessionRuntimeTest {
    @Test fun pauseInvalidatesOldFramesAndStopPreservesMarks() = runTest {
        val rig = SessionRig()
        assertTrue(rig.coordinator.pause().isFailure)
        assertTrue(rig.coordinator.stop().isFailure)
        rig.start()
        val old = rig.frame()
        assertTrue(rig.coordinator.accepts(old))
        assertTrue(rig.coordinator.start(SessionId("other")).isFailure)
        rig.coordinator.pause().getOrThrow()
        rig.now += 10_000
        rig.coordinator.checkCaptureHealth()
        assertEquals(SessionState.PAUSED, rig.coordinator.snapshot.value?.state)
        assertFalse(rig.coordinator.accepts(old))
        rig.coordinator.resume().getOrThrow()
        assertEquals(SessionState.PAUSED, rig.coordinator.snapshot.value?.state)
        rig.heartbeat()
        assertEquals(SessionState.STARTED, rig.coordinator.snapshot.value?.state)
        assertFalse(rig.coordinator.accepts(old))
        assertTrue(rig.coordinator.accepts(rig.frame()))
        rig.coordinator.stop().getOrThrow()
        val stopped = rig.coordinator.snapshot.value
        rig.now += 5_000
        rig.heartbeat()
        assertEquals(stopped, rig.coordinator.snapshot.value)
        assertEquals(listOf(TimelineEventType.SESSION_STARTED, TimelineEventType.SESSION_PAUSED,
            TimelineEventType.SESSION_RESUMED, TimelineEventType.SESSION_STOPPED),
            rig.timeline.events(rig.id).map { it.type })
        assertFalse(rig.audio.signals.contains(SessionSignal.CONNECTION_LOST))
    }

    @Test fun fourSecondsWithoutFramesRequiresNewGenerationBeforeRecovery() = runTest {
        val rig = SessionRig()
        rig.start()
        val old = rig.frame()
        rig.now = 3_999
        rig.coordinator.checkCaptureHealth()
        assertEquals(SessionState.STARTED, rig.coordinator.snapshot.value?.state)
        rig.now = 4_000
        rig.coordinator.checkCaptureHealth()
        assertEquals(setOf(DegradationCause.CAPTURE_UNAVAILABLE), rig.coordinator.snapshot.value?.degradationCauses)
        rig.coordinator.retryCapture().getOrThrow()
        rig.coordinator.onCaptureHeartbeat(CaptureHeartbeat(old.capture, rig.now))
        assertEquals(SessionState.DEGRADED, rig.coordinator.snapshot.value?.state)
        rig.heartbeat()
        assertEquals(SessionState.STARTED, rig.coordinator.snapshot.value?.state)
        assertTrue(rig.coordinator.accepts(rig.frame()))
        assertFalse(rig.coordinator.accepts(old))
        assertTrue(rig.timeline.events(rig.id).any { it.type == TimelineEventType.SESSION_DEGRADED })
        assertTrue(rig.timeline.events(rig.id).any { it.type == TimelineEventType.SESSION_RECOVERED })
    }

    @Test fun analysisFailureDoesNotHideCaptureFailure() = runTest {
        val rig = SessionRig()
        rig.start()
        rig.coordinator.onAnalysisUnavailable()
        rig.now = 4_000
        rig.coordinator.checkCaptureHealth()
        assertEquals(setOf(DegradationCause.ANALYSIS_UNAVAILABLE, DegradationCause.CAPTURE_UNAVAILABLE),
            rig.coordinator.snapshot.value?.degradationCauses)
        rig.coordinator.retryCapture().getOrThrow()
        rig.heartbeat()
        assertEquals(setOf(DegradationCause.ANALYSIS_UNAVAILABLE), rig.coordinator.snapshot.value?.degradationCauses)
        rig.coordinator.onAnalysisRestored()
        assertEquals(SessionState.STARTED, rig.coordinator.snapshot.value?.state)
    }
}

internal class SessionRig {
    var now = 0L
    val id = SessionId("integration-session")
    val clock = OliviaClock { ClockReading(now, 1_000_000 + now) }
    val gateway = TestGateway()
    val audio = TestAudio(clock)
    val timeline = InMemoryTimeline()
    val coordinator = SessionCoordinator(clock, gateway, audio, timeline)
    suspend fun start() { coordinator.start(id).getOrThrow(); heartbeat() }
    suspend fun heartbeat() {
        val beat = CaptureHeartbeat(requireNotNull(gateway.capture), now)
        gateway.heartbeat.value = beat
        coordinator.onCaptureHeartbeat(beat)
    }
    fun frame() = FrameStamp(requireNotNull(gateway.capture), now, clock.now())
}

internal class TestGateway : GlassesGateway {
    var capture: CaptureId? = null
    override val connection = MutableStateFlow(GlassesConnection.CONNECTED)
    override val frames = emptyFlow<Frame>()
    override val heartbeat = MutableStateFlow<CaptureHeartbeat?>(null)
    override suspend fun startCapture(capture: CaptureId) { this.capture = capture; heartbeat.value = null }
    override suspend fun pauseCapture() = Unit
    override suspend fun resumeCapture(capture: CaptureId) { this.capture = capture; heartbeat.value = null }
    override suspend fun stopCapture() = Unit
}

internal class TestAudio(private val clock: OliviaClock) : AudioFeedback {
    override val mode = AudioMode.VOICE
    val signals = mutableListOf<SessionSignal>()
    override suspend fun announce(correction: Correction) = Result.success(AudioReceipt(correction.id, correction.capture, clock.now()))
    override suspend fun signal(signal: SessionSignal): Result<ClockReading> {
        signals += signal
        return Result.success(clock.now())
    }
    override suspend fun stop() = Unit
}
