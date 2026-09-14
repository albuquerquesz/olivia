package com.olivia.companion.session

import android.content.Context
import com.olivia.companion.GlassesBackend
import com.olivia.companion.audio.AndroidAudioFeedback
import com.olivia.core.AudioMode
import com.olivia.core.CaptureHeartbeat
import com.olivia.core.CaptureId
import com.olivia.core.CorrectionStatus
import com.olivia.core.CorrectionTracker
import com.olivia.core.DefaultGuidanceEngine
import com.olivia.core.FileTimeline
import com.olivia.core.Frame
import com.olivia.core.FrameSampler
import com.olivia.core.FrameFormat
import com.olivia.core.FrameOrigin
import com.olivia.core.FrameStamp
import com.olivia.core.GlassesConnection
import com.olivia.core.GlassesGateway
import com.olivia.core.MockAnalysisReply
import com.olivia.core.MockFrameAnalyzer
import com.olivia.core.MockVisualFixtures
import com.olivia.core.OliviaClock
import com.olivia.core.LiveLoopMetrics
import com.olivia.core.LoopMeasurement
import com.olivia.core.LoopMeasurementId
import com.olivia.core.SamplingDecision
import com.olivia.core.SessionCoordinator
import com.olivia.core.SessionId
import com.olivia.core.SessionSnapshot
import com.olivia.core.SystemOliviaClock
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Commands supplied by a future on-device command recognizer; cloud speech is not accepted. */
enum class LocalSessionCommand { START, PAUSE, RESUME, STOP }

interface SessionRuntime : AutoCloseable {
    val snapshot: StateFlow<SessionSnapshot?>
    suspend fun handle(command: LocalSessionCommand): Result<SessionSnapshot>
}

/** The glasses flavor stays explicitly unavailable until INT-00 supplies a verified DAT adapter. */
object SessionRuntimeFactory {
    fun create(context: Context, backend: GlassesBackend): SessionRuntime =
        if (backend.id == "mock") MockSessionRuntime(context.applicationContext) else UnavailableSessionRuntime()
}

private class UnavailableSessionRuntime : SessionRuntime {
    private val state = MutableStateFlow<SessionSnapshot?>(null)
    override val snapshot: StateFlow<SessionSnapshot?> = state.asStateFlow()
    override suspend fun handle(command: LocalSessionCommand): Result<SessionSnapshot> =
        Result.failure(IllegalStateException("DAT ainda não está configurado"))
    override fun close() = Unit
}

/** Offline source: heartbeat is updated before each synthetic 720×1280 marker is emitted. */
private class MockGlassesGateway(
    private val clock: OliviaClock,
    private val scope: CoroutineScope,
) : GlassesGateway, AutoCloseable {
    private val _connection = MutableStateFlow(GlassesConnection.CONNECTED)
    private val _frames = MutableSharedFlow<Frame>(extraBufferCapacity = 1)
    private val _heartbeat = MutableStateFlow<CaptureHeartbeat?>(null)
    private var capture: CaptureId? = null
    private var source: Job? = null
    private var sequence: Long = 0

    override val connection: Flow<GlassesConnection> = _connection.asStateFlow()
    override val frames: Flow<Frame> = _frames.asSharedFlow()
    override val heartbeat: StateFlow<CaptureHeartbeat?> = _heartbeat.asStateFlow()

    override suspend fun startCapture(capture: CaptureId) {
        stopSource()
        this.capture = capture
        sequence = 0
        _heartbeat.value = null
        _connection.value = GlassesConnection.CONNECTED
        startSource(capture)
    }

    override suspend fun pauseCapture() { stopSource() }

    override suspend fun resumeCapture(capture: CaptureId) {
        stopSource()
        this.capture = capture
        _heartbeat.value = null
        startSource(capture)
    }

    override suspend fun stopCapture() {
        stopSource()
        capture = null
        _heartbeat.value = null
    }

    override fun close() { stopSource() }

    private fun startSource(activeCapture: CaptureId) {
        source = scope.launch {
            while (isActive && capture == activeCapture) {
                val now = clock.now()
                _heartbeat.value = CaptureHeartbeat(activeCapture, now.monotonicMs)
                _frames.emit(
                    Frame(
                        stamp = FrameStamp(activeCapture, sequence++, now),
                        width = 720,
                        height = 1_280,
                        format = FrameFormat.JPEG,
                        origin = FrameOrigin.MOCK,
                        bytes = byteArrayOf(),
                    ),
                )
                delay(500)
            }
        }
    }

    private fun stopSource() {
        source?.cancel()
        source = null
    }
}

/**
 * Full offline demo loop. Frames are synthetic markers, analyzed locally and discarded after
 * classification; this is deliberately not a DAT, Gemini, recording, or hardware claim.
 */
private class MockSessionRuntime(context: Context) : SessionRuntime {
    private val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val clock = SystemOliviaClock()
    private val gateway = MockGlassesGateway(clock, runtimeScope)
    private val audio = AndroidAudioFeedback(context, clock, AudioMode.VOICE)
    private val timeline = FileTimeline(context.filesDir.toPath().resolve("olivia-timeline"))
    private val coordinator = SessionCoordinator(clock, gateway, audio, timeline)
    private val sampler = FrameSampler(clock)
    private val analyzer = MockFrameAnalyzer(
        clock,
        mapOf(
            4L to MockAnalysisReply.Diagnosis(MockVisualFixtures.subjectRight.expectedDiagnoses),
            8L to MockAnalysisReply.Diagnosis(MockVisualFixtures.centered.expectedDiagnoses),
            12L to MockAnalysisReply.Diagnosis(MockVisualFixtures.handShadow.expectedDiagnoses),
            16L to MockAnalysisReply.Diagnosis(MockVisualFixtures.centered.expectedDiagnoses),
        ),
    )
    private val guidance = DefaultGuidanceEngine(AudioMode.VOICE)
    private val correctionTracker = CorrectionTracker()
    private val metrics = LiveLoopMetrics()
    /** Serializes coordinator, sampler and guidance because all three keep session state. */
    private val runtimeMutex = Mutex()
    private val heartbeatObserver: Job = runtimeScope.launch {
        gateway.heartbeat.filterNotNull().collect { heartbeat ->
            runtimeMutex.withLock { coordinator.onCaptureHeartbeat(heartbeat) }
        }
    }
    private val connectionObserver: Job = runtimeScope.launch {
        gateway.connection.collect { connection ->
            runtimeMutex.withLock {
                coordinator.onConnectionChanged(connection)
                expireIfDegraded()
            }
        }
    }
    private val frameObserver: Job = runtimeScope.launch {
        gateway.frames.collect { frame ->
            runtimeMutex.withLock {
                metrics.onFrameReceived()
                process(sampler.offer(frame, coordinator::accepts))
            }
        }
    }
    private val watchdog: Job = runtimeScope.launch {
        while (isActive) {
            runtimeMutex.withLock {
                coordinator.checkCaptureHealth()
                expireIfDegraded()
            }
            delay(250)
        }
    }

    override val snapshot: StateFlow<SessionSnapshot?> = coordinator.snapshot

    override suspend fun handle(command: LocalSessionCommand): Result<SessionSnapshot> = runtimeMutex.withLock {
        if (command in setOf(LocalSessionCommand.PAUSE, LocalSessionCommand.STOP)) expirePendingCorrection()
        if (command in setOf(LocalSessionCommand.START, LocalSessionCommand.STOP)) sampler.cancel()
        when (command) {
            LocalSessionCommand.START -> coordinator.start(SessionId(UUID.randomUUID().toString()))
            LocalSessionCommand.PAUSE -> coordinator.pause()
            LocalSessionCommand.RESUME -> coordinator.resume()
            LocalSessionCommand.STOP -> coordinator.stop()
        }
    }

    private suspend fun process(decisions: List<SamplingDecision>) {
        decisions.forEach { decision ->
            when (decision) {
                is SamplingDecision.Analyze -> {
                    val analysisStarted = clock.now()
                    var analysisFinished = analysisStarted
                    var audioRequestedAtMs: Long? = null
                    var audioStartedAtMs: Long? = null
                    try {
                        analyzer.analyze(decision.frame).also { analysisFinished = clock.now() }.onSuccess { analysis ->
                            coordinator.recordDiagnosis(analysis)
                            correctionTracker.onAnalysis(analysis, clock.now())?.let { outcome ->
                                guidance.onCorrectionOutcome(outcome.correction.id, outcome.status)
                                coordinator.recordCorrectionOutcome(outcome)
                            }
                            guidance.onAnalysis(analysis, clock.now())?.let { correction ->
                                audioRequestedAtMs = clock.now().monotonicMs
                                audio.announce(correction).onSuccess { receipt ->
                                    audioStartedAtMs = receipt.startedAt.monotonicMs
                                    if (correctionTracker.begin(correction, receipt)) {
                                        coordinator.recordCorrectionIssued(correction)
                                    }
                                }.onFailure {
                                    guidance.onCorrectionOutcome(correction.id, CorrectionStatus.EXPIRED)
                                }
                            }
                        }
                    } finally {
                        metrics.finish(
                            LoopMeasurement(
                                id = LoopMeasurementId(decision.frame.stamp.capture, decision.frame.stamp.sequence),
                                frameReceivedAtMs = decision.frame.stamp.receivedAt.monotonicMs,
                                analysisStartedAtMs = analysisStarted.monotonicMs,
                                analysisFinishedAtMs = analysisFinished.monotonicMs,
                                audioRequestedAtMs = audioRequestedAtMs,
                                audioStartedAtMs = audioStartedAtMs,
                            ),
                        )
                        // The Frame reference leaves scope here; nothing in the timeline stores it.
                        process(sampler.completeAnalysis())
                    }
                }
                is SamplingDecision.Discard -> metrics.onFrameDiscarded()
                SamplingDecision.Waiting -> Unit
            }
        }
    }

    private fun expirePendingCorrection() {
        correctionTracker.expire()?.let { outcome ->
            guidance.onCorrectionOutcome(outcome.correction.id, outcome.status)
            coordinator.recordCorrectionOutcome(outcome)
        }
    }

    private fun expireIfDegraded() {
        if (coordinator.snapshot.value?.state == com.olivia.core.SessionState.DEGRADED) {
            expirePendingCorrection()
        }
    }

    override fun close() {
        heartbeatObserver.cancel()
        connectionObserver.cancel()
        frameObserver.cancel()
        watchdog.cancel()
        gateway.close()
        audio.close()
        runtimeScope.cancel()
    }
}
