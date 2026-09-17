package com.olivia.companion.session

import com.olivia.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Synthetic markers only. A single producer owns sequence and monotonic scenario time. */
internal class ScenarioGlassesGateway(
    private val clock: OliviaClock = SystemOliviaClock(),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val fixtures: List<ScenarioFrame> = SessionScenario.sequence(),
) : GlassesGateway, AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _connection = MutableStateFlow(GlassesConnection.CONNECTED)
    private val _heartbeat = MutableStateFlow<CaptureHeartbeat?>(null)
    private val _frames = MutableSharedFlow<Frame>(extraBufferCapacity = 1)
    override val connection = _connection.asStateFlow()
    override val heartbeat = _heartbeat.asStateFlow()
    override val frames = _frames.asSharedFlow()
    private var producer: Job? = null
    private var capture: CaptureId? = null
    private var startedAtMs = 0L
    private var pausedAtMs: Long? = null
    private var sequence = 0L
    @Volatile private var connected = true

    override suspend fun startCapture(capture: CaptureId) {
        producer?.cancelAndJoin()
        this.capture = capture
        startedAtMs = clock.now().monotonicMs
        pausedAtMs = null
        sequence = 0
        connected = true
        _heartbeat.value = null
        _connection.value = GlassesConnection.CONNECTED
        startProducer()
    }

    override suspend fun pauseCapture() {
        producer?.cancelAndJoin()
        producer = null
        pausedAtMs = clock.now().monotonicMs
    }

    override suspend fun resumeCapture(capture: CaptureId) {
        producer?.cancelAndJoin()
        pausedAtMs?.let { startedAtMs += clock.now().monotonicMs - it }
        pausedAtMs = null
        this.capture = capture
        _heartbeat.value = null
        startProducer()
    }

    override suspend fun stopCapture() {
        producer?.cancelAndJoin()
        producer = null
        capture = null
        _heartbeat.value = null
    }

    fun setConnected(value: Boolean) {
        connected = value
        _connection.value = if (value) GlassesConnection.CONNECTED else GlassesConnection.DISCONNECTED
    }

    private fun startProducer() {
        val generation = capture ?: return
        producer = scope.launch {
            while (isActive) {
                val now = clock.now()
                val offset = now.monotonicMs - startedAtMs
                val slot = offset / SessionScenario.FRAME_INTERVAL_MS * SessionScenario.FRAME_INTERVAL_MS
                val fixture = fixtures.lastOrNull { it.offsetMs <= slot }
                val gap = offset in SessionScenario.DEGRADED_GAP_START_MS until SessionScenario.DEGRADED_GAP_END_MS
                if (connected && !gap && fixture != null) {
                    _connection.value = GlassesConnection.CONNECTED
                    _heartbeat.value = CaptureHeartbeat(generation, now.monotonicMs)
                    _frames.emit(Frame(
                        FrameStamp(generation, sequence++, now), SessionScenario.FRAME_WIDTH,
                        SessionScenario.FRAME_HEIGHT, FrameFormat.RGBA_8888, FrameOrigin.MOCK,
                        byteArrayOf(fixture.marker.toByte()),
                    ))
                }
                delay(500)
            }
        }
    }

    override fun close() { scope.cancel() }
}
