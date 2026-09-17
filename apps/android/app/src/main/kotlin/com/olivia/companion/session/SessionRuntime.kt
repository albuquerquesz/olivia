package com.olivia.companion.session

import android.content.Context
import android.util.Log
import com.olivia.companion.GlassesBackend
import com.olivia.companion.audio.AndroidAudioFeedback
import com.olivia.core.AudioMode
import com.olivia.core.AnalysisFailure
import com.olivia.core.CaptureHeartbeat
import com.olivia.core.CaptureId
import com.olivia.core.Correction
import com.olivia.core.CorrectionStatus
import com.olivia.core.CorrectionTracker
import com.olivia.core.DefaultGuidanceEngine
import com.olivia.core.FileTimeline
import com.olivia.core.Frame
import com.olivia.core.FrameAnalyzer
import com.olivia.core.FastFrameProcessor
import com.olivia.core.HybridVisualAnalyzer
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
import com.olivia.core.GuidancePolicy
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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Commands supplied by a future on-device command recognizer; cloud speech is not accepted. */
enum class LocalSessionCommand { START, PAUSE, RESUME, STOP }

/** Content-free live status for the phone UI; it never exposes frames or model text. */
enum class LiveGuidanceStatus {
    IDLE,
    PAUSED,
    LOCATING_TARGET,
    TARGET_NOT_FOUND,
    TARGET_FOUND,
    TARGET_CENTERED,
    GUIDANCE_PENDING,
    GUIDANCE_SENT,
    ANALYSIS_UNAVAILABLE,
    AUDIO_UNAVAILABLE,
}

interface SessionRuntime : AutoCloseable {
    val snapshot: StateFlow<SessionSnapshot?>
    /** Aggregate-only, process-local measurements; never contains media, model text or identifiers. */
    val liveLoopMetrics: StateFlow<LiveLoopMetricSnapshot>
    /** Human-readable live-loop phase for the phone UI, not a substitute for audio guidance. */
    val liveGuidanceStatus: StateFlow<LiveGuidanceStatus>
    /** Local aggregate index used by the Sessions screen; no media or frame data is retained. */
    val sessionHistory: StateFlow<List<SessionHistoryEntry>>
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
    private val guidance = MutableStateFlow(LiveGuidanceStatus.IDLE)
    override val snapshot: StateFlow<SessionSnapshot?> = state.asStateFlow()
    override val liveLoopMetrics: StateFlow<LiveLoopMetricSnapshot> = metrics.asStateFlow()
    override val liveGuidanceStatus: StateFlow<LiveGuidanceStatus> = guidance.asStateFlow()
    override val sessionHistory: StateFlow<List<SessionHistoryEntry>> =
        MutableStateFlow<List<SessionHistoryEntry>>(emptyList()).asStateFlow()
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
    private val clock: OliviaClock,
) : SessionRuntime {
    private val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val audio = AndroidAudioFeedback(context, clock, AudioMode.VOICE)
    private val timeline = FileTimeline(context.filesDir.toPath().resolve("olivia-timeline"))
    private val coordinator = SessionCoordinator(clock, gateway, audio, timeline)
    private val fastProcessor = analyzer as? FastFrameProcessor
    // Hybrid tracking carries the live loop locally; Gemini is a validation/reaquisition path
    // every five seconds instead of being on the 0.5 fps critical path.
    private val sampler = FrameSampler(
        clock = clock,
        analysisIntervalMs = if (fastProcessor != null) 5_000 else 2_000,
    )
    /**
     * The engine owns the local phrase catalogue. Recreate it only while idle so its mode always
     * matches the output adapter and no pending correction can be lost mid-take.
     */
    private var guidance = createGuidance(AudioMode.VOICE)
    private var audioMode = AudioMode.VOICE
    private val correctionTracker = CorrectionTracker()
    private val metrics = LiveLoopMetrics()
    private val _liveLoopMetrics = MutableStateFlow(metrics.snapshot())
    override val liveLoopMetrics: StateFlow<LiveLoopMetricSnapshot> = _liveLoopMetrics.asStateFlow()
    private val _liveGuidanceStatus = MutableStateFlow(LiveGuidanceStatus.IDLE)
    override val liveGuidanceStatus: StateFlow<LiveGuidanceStatus> = _liveGuidanceStatus.asStateFlow()
    private val historyStore = LocalSessionHistory(context)
    override val sessionHistory: StateFlow<List<SessionHistoryEntry>> = historyStore.entries.stateIn(
        runtimeScope,
        SharingStarted.Eagerly,
        emptyList(),
    )
    /** Serializes short state transitions; provider, TTS and frame I/O never run under this lock. */
    private val runtimeMutex = Mutex()
    private val analysisFrames = Channel<Frame>(capacity = 1)
    private var activeAnalysisJob: Job? = null
    private val analysisWorker: Job = runtimeScope.launch {
        for (frame in analysisFrames) {
            val job = launch(start = CoroutineStart.LAZY) { processAnalyze(frame) }
            runtimeMutex.withLock { activeAnalysisJob = job }
            job.start()
            job.join()
            runtimeMutex.withLock {
                if (activeAnalysisJob === job) activeAnalysisJob = null
            }
        }
    }
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
            val fastStartedAt = clock.now()
            val fastResult = fastProcessor?.processFast(frame)
            val decisions = runtimeMutex.withLock {
                metrics.onFrameReceived()
                publishMetrics()
                sampler.offer(frame, coordinator::accepts)
            }
            if (fastResult != null) {
                runtimeScope.launch {
                    processFastAnalysis(frame.stamp, fastResult, fastStartedAt)
                }
            }
            dispatchDecisions(decisions)
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

    override suspend fun handle(command: LocalSessionCommand): Result<SessionSnapshot> {
        // A pause/stop must cancel an in-flight provider or TTS call before invalidating the
        // capture generation. This prevents an old result from speaking after the transition.
        val cancellable = if (command in setOf(LocalSessionCommand.PAUSE, LocalSessionCommand.STOP)) {
            runtimeMutex.withLock {
                expirePendingCorrection()
                discard(sampler.cancel())
                activeAnalysisJob
            }
        } else {
            null
        }
        cancellable?.cancelAndJoin()
        val result = runtimeMutex.withLock {
            if (command == LocalSessionCommand.START) discard(sampler.cancel())
            when (command) {
                LocalSessionCommand.START -> {
                    (analyzer as? HybridVisualAnalyzer)?.reset()
                    _liveGuidanceStatus.value = LiveGuidanceStatus.LOCATING_TARGET
                    coordinator.start(
                        sessionId = SessionId(UUID.randomUUID().toString()),
                        config = SessionConfig(audioMode = audioMode),
                    )
                }
                LocalSessionCommand.PAUSE -> {
                    (analyzer as? HybridVisualAnalyzer)?.reset()
                    coordinator.pause()
                }
                LocalSessionCommand.RESUME -> {
                    (analyzer as? HybridVisualAnalyzer)?.reset()
                    coordinator.resume()
                }
                LocalSessionCommand.STOP -> {
                    (analyzer as? HybridVisualAnalyzer)?.reset()
                    coordinator.stop()
                }
            }
        }
        if (command == LocalSessionCommand.START && result.isFailure) {
            _liveGuidanceStatus.value = LiveGuidanceStatus.IDLE
        }
        if (command == LocalSessionCommand.RESUME && result.isSuccess) {
            _liveGuidanceStatus.value = LiveGuidanceStatus.LOCATING_TARGET
        }
        if (command == LocalSessionCommand.STOP && result.isSuccess) {
            val stopped = result.getOrThrow()
            historyStore.record(stopped, timeline.events(stopped.id))
            _liveGuidanceStatus.value = LiveGuidanceStatus.IDLE
        }
        if (command == LocalSessionCommand.PAUSE && result.isSuccess) {
            _liveGuidanceStatus.value = LiveGuidanceStatus.PAUSED
        }
        return result
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
        guidance = createGuidance(mode)
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

    /** Hands decisions to the single analysis worker without holding the state lock. */
    private suspend fun dispatchDecisions(decisions: List<SamplingDecision>) {
        decisions.forEach { decision ->
            when (decision) {
                is SamplingDecision.Analyze -> analysisFrames.send(decision.frame)
                is SamplingDecision.Discard -> runtimeMutex.withLock { discard(decision) }
                SamplingDecision.Waiting -> Unit
            }
        }
    }

    /** Runs provider and TTS work outside [runtimeMutex], then commits only current results. */
    private suspend fun processAnalyze(frame: Frame) {
        val analysisStarted = clock.now()
        var analysisFinished: com.olivia.core.ClockReading? = null
        var audioRequestedAtMs: Long? = null
        var audioStartedAtMs: Long? = null
        try {
            val accepted = runtimeMutex.withLock { coordinator.accepts(frame.stamp) }
            if (!accepted) {
                runtimeMutex.withLock {
                    metrics.onFrameDiscarded()
                    publishMetrics()
                }
                return
            }
            val result = analyzer.analyze(frame).also { analysisFinished = clock.now() }
            if (result.isFailure) {
                if (com.olivia.companion.BuildConfig.DEBUG) {
                    val code = (result.exceptionOrNull() as? AnalysisFailure)?.code?.name
                        ?: result.exceptionOrNull()?.javaClass?.simpleName
                        ?: "UNKNOWN"
                    Log.w(LOG_TAG, "visual analysis unavailable: $code")
                }
                _liveGuidanceStatus.value = LiveGuidanceStatus.ANALYSIS_UNAVAILABLE
                runtimeMutex.withLock { coordinator.onAnalysisUnavailable() }
                return
            }
            val analysis = result.getOrThrow()
            val hasTrackedTarget = (analyzer as? HybridVisualAnalyzer)?.currentTrackedRoi() != null
            _liveGuidanceStatus.value = if (!hasTrackedTarget && analyzer is HybridVisualAnalyzer) {
                LiveGuidanceStatus.TARGET_NOT_FOUND
            } else if (analysis.hasDeviation) {
                LiveGuidanceStatus.TARGET_FOUND
            } else {
                LiveGuidanceStatus.TARGET_CENTERED
            }
            var correction: Correction? = null
            val stillAccepted = runtimeMutex.withLock {
                if (!coordinator.accepts(analysis.frameStamp)) {
                    false
                } else {
                    coordinator.onAnalysisRestored()
                    coordinator.recordDiagnosis(analysis)
                    correctionTracker.onAnalysis(analysis, clock.now())?.let { outcome ->
                        guidance.onCorrectionOutcome(outcome.correction.id, outcome.status)
                        coordinator.recordCorrectionOutcome(outcome)
                    }
                    correction = guidance.onAnalysis(analysis, clock.now())
                    true
                }
            }
            if (stillAccepted && correction != null) {
                val issuedCorrection = requireNotNull(correction)
                _liveGuidanceStatus.value = LiveGuidanceStatus.GUIDANCE_PENDING
                audioRequestedAtMs = clock.now().monotonicMs
                val audioResult = audio.announce(issuedCorrection)
                if (audioResult.isSuccess) {
                    val receipt = audioResult.getOrThrow()
                    audioStartedAtMs = receipt.startedAt.monotonicMs
                    runtimeMutex.withLock {
                        // A stop/pause may have invalidated the generation while TTS was running.
                        if (coordinator.accepts(FrameStamp(issuedCorrection.capture, 0, issuedCorrection.issuedAt)) &&
                            correctionTracker.begin(issuedCorrection, receipt)
                        ) {
                            coordinator.recordCorrectionIssued(issuedCorrection)
                            _liveGuidanceStatus.value = LiveGuidanceStatus.GUIDANCE_SENT
                        } else {
                            guidance.onCorrectionOutcome(issuedCorrection.id, CorrectionStatus.EXPIRED)
                        }
                    }
                } else {
                    _liveGuidanceStatus.value = LiveGuidanceStatus.AUDIO_UNAVAILABLE
                    runtimeMutex.withLock {
                        guidance.onCorrectionOutcome(issuedCorrection.id, CorrectionStatus.EXPIRED)
                    }
                }
            }
        } finally {
            val next = runtimeMutex.withLock {
                metrics.finish(
                    LoopMeasurement(
                        id = LoopMeasurementId(frame.stamp.capture, frame.stamp.sequence),
                        frameReceivedAtMs = frame.stamp.receivedAt.monotonicMs,
                        analysisStartedAtMs = analysisStarted.monotonicMs,
                        analysisFinishedAtMs = (analysisFinished ?: clock.now()).monotonicMs,
                        audioRequestedAtMs = audioRequestedAtMs,
                        audioStartedAtMs = audioStartedAtMs,
                    ),
                )
                publishMetrics()
                sampler.completeAnalysis()
            }
            // The Frame reference leaves scope here; nothing in the timeline stores it.
            dispatchDecisions(next)
        }
    }

    /** Commits a tracker result without touching the slow sampler or retaining the frame bytes. */
    private suspend fun processFastAnalysis(
        stamp: FrameStamp,
        result: Result<com.olivia.core.FrameAnalysis>,
        startedAt: com.olivia.core.ClockReading,
    ) {
        var audioRequestedAtMs: Long? = null
        var audioStartedAtMs: Long? = null
        val analysisStartedAtMs = startedAt.monotonicMs.coerceAtLeast(stamp.receivedAt.monotonicMs)
        var analysisFinishedAtMs: Long? = null
        try {
            val analysis = result.getOrNull()
            if (analysis == null) {
                _liveGuidanceStatus.value = LiveGuidanceStatus.ANALYSIS_UNAVAILABLE
                return
            }
            var correction: Correction? = null
            val accepted = runtimeMutex.withLock {
                if (!coordinator.accepts(analysis.frameStamp)) false
                else {
                    coordinator.onAnalysisRestored()
                    coordinator.recordDiagnosis(analysis)
                    correctionTracker.onAnalysis(analysis, clock.now())?.let { outcome ->
                        guidance.onCorrectionOutcome(outcome.correction.id, outcome.status)
                        coordinator.recordCorrectionOutcome(outcome)
                    }
                    correction = guidance.onAnalysis(analysis, clock.now())
                    true
                }
            }
            // The fast analysis has completed before any TTS or signal request is made. Keeping
            // this boundary explicit preserves the metric contract:
            // analysisFinishedAt <= audioRequestedAt <= audioStartedAt.
            analysisFinishedAtMs = clock.now().monotonicMs.coerceAtLeast(analysisStartedAtMs)
            if (accepted) {
                _liveGuidanceStatus.value = if (analysis.hasDeviation) {
                    LiveGuidanceStatus.TARGET_FOUND
                } else {
                    LiveGuidanceStatus.TARGET_CENTERED
                }
            }
            if (accepted && correction != null) {
                val issued = requireNotNull(correction)
                _liveGuidanceStatus.value = LiveGuidanceStatus.GUIDANCE_PENDING
                audioRequestedAtMs = clock.now().monotonicMs
                val audioResult = audio.announce(issued)
                if (audioResult.isSuccess) {
                    val receipt = audioResult.getOrThrow()
                    audioStartedAtMs = receipt.startedAt.monotonicMs
                    runtimeMutex.withLock {
                        if (coordinator.accepts(FrameStamp(issued.capture, 0, issued.issuedAt)) &&
                            correctionTracker.begin(issued, receipt)
                        ) {
                            coordinator.recordCorrectionIssued(issued)
                            _liveGuidanceStatus.value = LiveGuidanceStatus.GUIDANCE_SENT
                        }
                        else guidance.onCorrectionOutcome(issued.id, CorrectionStatus.EXPIRED)
                    }
                } else {
                    _liveGuidanceStatus.value = LiveGuidanceStatus.AUDIO_UNAVAILABLE
                    runtimeMutex.withLock { guidance.onCorrectionOutcome(issued.id, CorrectionStatus.EXPIRED) }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            // A diagnostic or local fast-path failure must not bring down the live session.
            if (com.olivia.companion.BuildConfig.DEBUG) {
                Log.e(LOG_TAG, "fast analysis unavailable", error)
            }
            _liveGuidanceStatus.value = LiveGuidanceStatus.ANALYSIS_UNAVAILABLE
        } finally {
            runtimeMutex.withLock {
                val finished = analysisFinishedAtMs ?: clock.now().monotonicMs
                metrics.finishFast(
                    LoopMeasurement(
                        id = LoopMeasurementId(stamp.capture, stamp.sequence),
                        frameReceivedAtMs = stamp.receivedAt.monotonicMs,
                        analysisStartedAtMs = analysisStartedAtMs,
                        analysisFinishedAtMs = finished.coerceAtLeast(analysisStartedAtMs),
                        audioRequestedAtMs = audioRequestedAtMs,
                        audioStartedAtMs = audioStartedAtMs,
                    ),
                )
                publishMetrics()
            }
        }
    }

    private fun discard(decision: SamplingDecision.Discard) {
        metrics.onFrameDiscarded()
        publishMetrics()
    }

    private fun discard(decisions: List<SamplingDecision>) {
        decisions.filterIsInstance<SamplingDecision.Discard>().forEach(::discard)
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

    private fun createGuidance(mode: AudioMode): DefaultGuidanceEngine =
        if (fastProcessor != null) {
            DefaultGuidanceEngine(
                mode,
                GuidancePolicy(requiredConsecutiveMatches = 1, persistenceMs = 180),
            )
        } else DefaultGuidanceEngine(mode)

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
        analysisFrames.close()
        analysisWorker.cancel()
        (gateway as? AutoCloseable)?.close()
        audio.close()
        runtimeScope.cancel()
    }
}

private const val LOG_TAG = "OliviaSessionRuntime"

fun createMockSessionRuntime(context: Context): SessionRuntime {
    val clock = SystemOliviaClock()
    return LiveSessionRuntime(
        context = context,
        gateway = MockGlassesGateway(
            clock = clock,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        ),
        analyzer = MockFrameAnalyzer(
            clock,
            mapOf(
                4L to MockAnalysisReply.Diagnosis(MockVisualFixtures.subjectRight.expectedDiagnoses),
                8L to MockAnalysisReply.Diagnosis(MockVisualFixtures.centered.expectedDiagnoses),
                12L to MockAnalysisReply.Diagnosis(MockVisualFixtures.handShadow.expectedDiagnoses),
                16L to MockAnalysisReply.Diagnosis(MockVisualFixtures.centered.expectedDiagnoses),
            ),
        ),
        clock = clock,
    )
}
