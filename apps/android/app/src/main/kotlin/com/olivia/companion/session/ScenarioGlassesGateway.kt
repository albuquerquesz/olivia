package com.olivia.companion.session

import com.olivia.core.Frame
import com.olivia.core.FrameFormat
import com.olivia.core.FrameOrigin
import com.olivia.core.GlassesConnection
import com.olivia.core.GlassesGateway
import com.olivia.core.SessionConfig
import com.olivia.core.SessionId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class ScenarioGlassesGateway(
    private val clock: () -> Long = { System.currentTimeMillis() },
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val fixtures: List<ScenarioFrame> = SessionScenario.sequence(),
) : GlassesGateway, AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val lock = Mutex()
    private val _connection = MutableStateFlow(GlassesConnection.CONNECTED)
    private val _frames = MutableSharedFlow<Frame>(extraBufferCapacity = 64)

    override val connection: StateFlow<GlassesConnection> = _connection.asStateFlow()
    override val frames: SharedFlow<Frame> = _frames.asSharedFlow()

    private var producer: Job? = null
    private var sessionId: SessionId? = null
    private var startedAtEpochMs = 0L
    private var pausedAtEpochMs: Long? = null
    private var nextFixtureIndex = 0

    override suspend fun startCapture(sessionId: SessionId, config: SessionConfig) {
        lock.withLock {
            producer?.cancel()
            this.sessionId = sessionId
            startedAtEpochMs = clock()
            pausedAtEpochMs = null
            nextFixtureIndex = 0
            _connection.value = GlassesConnection.CONNECTED
            producer = scope.launch { emitFixtures() }
        }
    }

    override suspend fun pauseCapture() {
        lock.withLock {
            pausedAtEpochMs = clock()
            producer?.cancel()
            producer = null
        }
    }

    override suspend fun resumeCapture() {
        lock.withLock {
            val pausedAt = pausedAtEpochMs ?: return
            startedAtEpochMs += clock() - pausedAt
            pausedAtEpochMs = null
            producer = scope.launch { emitFixtures() }
        }
    }

    override suspend fun stopCapture() {
        lock.withLock {
            producer?.cancel()
            producer = null
            sessionId = null
            pausedAtEpochMs = null
        }
    }

    override fun close() {
        scope.cancel()
    }

    private suspend fun emitFixtures() {
        while (scope.isActive && nextFixtureIndex < fixtures.size) {
            val fixture = fixtures[nextFixtureIndex]
            val targetEpochMs = startedAtEpochMs + fixture.offsetMs
            val previousOffsetMs = fixtures
                .getOrNull(nextFixtureIndex - 1)
                ?.offsetMs
                ?: fixture.offsetMs
            val gapMs = fixture.offsetMs - previousOffsetMs
            if (gapMs > SessionScenario.FRAME_INTERVAL_MS) {
                val disconnectAtEpochMs = startedAtEpochMs + previousOffsetMs + 4_000L
                delay((disconnectAtEpochMs - clock()).coerceAtLeast(0))
                if (!scope.isActive) return
                _connection.value = GlassesConnection.DISCONNECTED
            }
            delay((targetEpochMs - clock()).coerceAtLeast(0))
            if (!scope.isActive) return
            val currentSessionId = sessionId ?: return
            _frames.emit(
                Frame(
                    sessionId = currentSessionId,
                    capturedAtEpochMs = targetEpochMs,
                    width = SessionScenario.FRAME_WIDTH,
                    height = SessionScenario.FRAME_HEIGHT,
                    format = FrameFormat.RGBA_8888,
                    origin = FrameOrigin.MOCK,
                    bytes = byteArrayOf(fixture.marker.toByte()),
                ),
            )
            _connection.value = GlassesConnection.CONNECTED
            nextFixtureIndex += 1
        }
    }
}
