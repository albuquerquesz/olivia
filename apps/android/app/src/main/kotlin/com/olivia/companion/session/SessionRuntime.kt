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
import com.olivia.core.FrameAnalyzer
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
import com.olivia.core.LiveLoopMetricSnapshot
import com.olivia.core.LoopMeasurement
import com.olivia.core.LoopMeasurementId
import com.olivia.core.SamplingDecision
import com.olivia.core.SessionCoordinator
import com.olivia.core.SessionId
import com.olivia.core.SessionReview
import com.olivia.core.SessionConfig
import com.olivia.core.TimelineEvent
import com.olivia.core.SessionSnapshot
import com.olivia.core.SystemOliviaClock
import java.util.UUID
import kotlinx.coroutines.CancellationException
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
    /** Aggregate-only, process-local measurements; never contains media, model text or identifiers. */
    val liveLoopMetrics: StateFlow<LiveLoopMetricSnapshot>
    suspend fun handle(command: LocalSessionCommand): Result<SessionSnapshot>
    suspend fun setAudioMode(mode: AudioMode): Result<Unit>
    suspend fun timelineFor(sessionId: SessionId): Result<List<TimelineEvent>>
    suspend fun processPostTake(work: suspend (SessionId) -> Result<SessionReview>): Result<SessionReview>
}

/** Variant boundary: mock stays offline; the glasses source set owns the DAT/Firebase wiring. */
interface BackendRuntimeProvider {
    fun create(context: Context): SessionRuntime
}

object SessionRuntimeFactory {
    fun create(context: Context, backend: GlassesBackend): SessionRuntime = try {
        val providerClass = Class.forName("com.olivia.companion.VariantRuntimeProvider")
        val provider = providerClass.getField("INSTANCE").get(null) as BackendRuntimeProvider
        provider.create(context.applicationContext)
    } catch (_: Throwable) {
        UnavailableSessionRuntime()
    }
}

class UnavailableSessionRuntime : SessionRuntime {
    private val state = MutableStateFlow<SessionSnapshot?>(null)
    private val metrics = MutableStateFlow(LiveLoopMetrics().snapshot())
    override val snapshot: StateFlow<SessionSnapshot?> = state.asStateFlow()
    override val liveLoopMetrics: StateFlow<LiveLoopMetricSnapshot> = metrics.asStateFlow()
    override suspend fun handle(command: LocalSessionCommand): Result<SessionSnapshot> =
        Result.failure(IllegalStateException("DAT ainda não está configurado"))
    override suspend fun setAudioMode(mode: AudioMode): Result<Unit> = Result.success(Unit)
    override suspend fun timelineFor(sessionId: SessionId): Result<List<TimelineEvent>> =
        Result.failure(IllegalStateException("DAT ainda não está configurado"))
    override suspend fun processPostTake(work: suspend (SessionId) -> Result<SessionReview>): Result<SessionReview> =
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
internal class LiveSessionRuntime(
    context: Context,
    private val gateway: GlassesGateway,
    private val analyzer: FrameAnalyzer,
) : SessionRuntime {
    private val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val clock = SystemOliviaClock()
    private val audio = AndroidAudioFeedback(context, clock, AudioMode.VOICE)
    private val timeline = FileTimeline(context.filesDir.toPath().resolve("olivia-timeline"))
    private val coordinator = SessionCoordinator(clock, gateway, audio, timeline)
    private val sampler = FrameSampler(clock)
    /**
     * The engine owns the local phrase catalogue. Recreate it only while idle so its mode always
     * matches the output adapter and no pending correction can be lost mid-take.
     */
    private var guidance = DefaultGuidanceEngine(AudioMode.VOICE)
    private var audioMode = AudioMode.VOICE
    private val correctionTracker = CorrectionTracker()
    private val metrics = LiveLoopMetrics()
    private val _liveLoopMetrics = MutableStateFlow(metrics.snapshot())
    override val liveLoopMetrics: StateFlow<LiveLoopMetricSnapshot> = _liveLoopMetrics.asStateFlow()
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
                publishMetrics()
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
            LocalSessionCommand.START -> coordinator.start(
                sessionId = SessionId(UUID.randomUUID().toString()),
                config = SessionConfig(audioMode = audioMode),
            )
            LocalSessionCommand.PAUSE -> coordinator.pause()
            LocalSessionCommand.RESUME -> coordinator.resume()
            LocalSessionCommand.STOP -> coordinator.stop()
        }
    }

    override suspend fun setAudioMode(mode: AudioMode): Result<Unit> = runtimeMutex.withLock {
        val state = coordinator.snapshot.value?.state
        if (state in setOf(
                com.olivia.core.SessionState.STARTING,
                com.olivia.core.SessionState.STARTED,
                com.olivia.core.SessionState.PAUSED,
                com.olivia.core.SessionState.DEGRADED,
                com.olivia.core.SessionState.STOPPING,
            )
        ) {
            return@withLock Result.failure(IllegalStateException("Altere o modo antes do take"))
        }
        audio.setMode(mode)
        guidance = DefaultGuidanceEngine(mode)
        audioMode = mode
        Result.success(Unit)
    }

    override suspend fun timelineFor(sessionId: SessionId): Result<List<TimelineEvent>> = runtimeMutex.withLock {
        if (coordinator.snapshot.value?.id != sessionId) {
            Result.failure(IllegalArgumentException("Timeline fora da sessão ativa"))
        } else {
            Result.success(timeline.events(sessionId))
        }
    }

    override suspend fun processPostTake(work: suspend (SessionId) -> Result<SessionReview>): Result<SessionReview> {
        val started = runtimeMutex.withLock { coordinator.beginPostProcessing() }
        val sessionId = started.getOrElse { return Result.failure(it) }.id
        return try {
            work(sessionId).also { result ->
                runtimeMutex.withLock {
                    if (result.isSuccess) coordinator.completePostProcessing(sessionId)
                    else coordinator.failPostProcessing(sessionId)
                }
            }
        } catch (error: CancellationException) {
            runtimeMutex.withLock { coordinator.cancelPostProcessing(sessionId) }
            throw error
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
                            coordinator.onAnalysisRestored()
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
                        }.onFailure {
                            coordinator.onAnalysisUnavailable()
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
                        publishMetrics()
                        // The Frame reference leaves scope here; nothing in the timeline stores it.
                        process(sampler.completeAnalysis())
                    }
                }
                is SamplingDecision.Discard -> {
                    metrics.onFrameDiscarded()
                    publishMetrics()
                }
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

    private fun publishMetrics() {
        _liveLoopMetrics.value = metrics.snapshot()
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
        (gateway as? AutoCloseable)?.close()
        audio.close()
        runtimeScope.cancel()
    }
}

fun createMockSessionRuntime(context: Context): SessionRuntime = LiveSessionRuntime(
    context = context,
    gateway = MockGlassesGateway(
        clock = SystemOliviaClock(),
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    ),
    analyzer = MockFrameAnalyzer(
        SystemOliviaClock(),
        mapOf(
            4L to MockAnalysisReply.Diagnosis(MockVisualFixtures.subjectRight.expectedDiagnoses),
            8L to MockAnalysisReply.Diagnosis(MockVisualFixtures.centered.expectedDiagnoses),
            12L to MockAnalysisReply.Diagnosis(MockVisualFixtures.handShadow.expectedDiagnoses),
            16L to MockAnalysisReply.Diagnosis(MockVisualFixtures.centered.expectedDiagnoses),
        ),
    ),
)
