package com.olivia.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SessionCoordinatorTest {
    @Test
    fun `capture evidence starts the session and four seconds without a frame degrades it`() = runBlocking {
        val clock = MutableSessionClock()
        val gateway = FakeGateway()
        val timeline = InMemoryTimeline()
        val coordinator = SessionCoordinator(clock, gateway, FakeAudio(clock), timeline)

        coordinator.start(SessionId("take"))
        assertEquals(SessionState.STARTING, coordinator.snapshot.value?.state)

        val heartbeat = gateway.emitHeartbeat(clock.now().monotonicMs)
        coordinator.onCaptureHeartbeat(heartbeat)
        assertEquals(SessionState.STARTED, coordinator.snapshot.value?.state)

        clock.advanceBy(3_999)
        coordinator.checkCaptureHealth()
        assertEquals(SessionState.STARTED, coordinator.snapshot.value?.state)
        clock.advanceBy(1)
        coordinator.checkCaptureHealth()

        assertEquals(SessionState.DEGRADED, coordinator.snapshot.value?.state)
        assertEquals(
            setOf(DegradationCause.CAPTURE_UNAVAILABLE),
            coordinator.snapshot.value?.degradationCauses,
        )
        assertEquals(
            listOf(TimelineEventType.SESSION_STARTED, TimelineEventType.SESSION_DEGRADED),
            timeline.events(SessionId("take")).map { it.type },
        )
    }

    @Test
    fun `pause disarms the watchdog and resume requires a new heartbeat`() = runBlocking {
        val clock = MutableSessionClock()
        val gateway = FakeGateway()
        val coordinator = SessionCoordinator(clock, gateway, FakeAudio(clock), InMemoryTimeline())

        coordinator.start(SessionId("take"))
        coordinator.onCaptureHeartbeat(gateway.emitHeartbeat(clock.now().monotonicMs))
        coordinator.pause()
        clock.advanceBy(10_000)
        coordinator.checkCaptureHealth()
        assertEquals(SessionState.PAUSED, coordinator.snapshot.value?.state)

        coordinator.resume()
        assertEquals(SessionState.PAUSED, coordinator.snapshot.value?.state)
        coordinator.onCaptureHeartbeat(gateway.emitHeartbeat(clock.now().monotonicMs))
        assertEquals(SessionState.STARTED, coordinator.snapshot.value?.state)
        assertEquals(2, gateway.resumeCount)
    }
}

private class MutableSessionClock : OliviaClock {
    private var monotonicMs: Long = 0

    override fun now(): ClockReading = ClockReading(monotonicMs, 1_700_000_000_000 + monotonicMs)

    fun advanceBy(value: Long) {
        monotonicMs += value
    }
}

private class FakeGateway : GlassesGateway {
    override val connection: Flow<GlassesConnection> = emptyFlow()
    override val frames: Flow<Frame> = emptyFlow()
    override val heartbeat: MutableStateFlow<CaptureHeartbeat?> = MutableStateFlow(null)
    private var active: CaptureId? = null
    var resumeCount: Int = 0
        private set

    override suspend fun startCapture(capture: CaptureId) {
        active = capture
        resumeCount += 1
    }

    override suspend fun pauseCapture() = Unit

    override suspend fun resumeCapture(capture: CaptureId) {
        active = capture
        resumeCount += 1
    }

    override suspend fun stopCapture() = Unit

    fun emitHeartbeat(monotonicMs: Long): CaptureHeartbeat {
        val value = CaptureHeartbeat(checkNotNull(active), monotonicMs)
        heartbeat.value = value
        return value
    }
}

private class FakeAudio(private val clock: OliviaClock) : AudioFeedback {
    override val mode: AudioMode = AudioMode.VOICE

    override suspend fun announce(correction: Correction): Result<AudioReceipt> =
        Result.success(AudioReceipt(correction.id, correction.capture, clock.now()))

    override suspend fun signal(signal: SessionSignal): Result<ClockReading> = Result.success(clock.now())

    override suspend fun stop() = Unit
}
