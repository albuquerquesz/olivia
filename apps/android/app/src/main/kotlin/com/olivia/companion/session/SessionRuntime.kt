package com.olivia.companion.session

import android.app.Activity
import android.content.Context
import androidx.activity.ComponentActivity
import android.util.Log
import com.olivia.companion.GlassesBackend
import com.olivia.companion.audio.AndroidAudioFeedback
import com.olivia.companion.audio.AudioTimingSink
import com.olivia.core.AudioMode
import com.olivia.core.AnalysisFailure
import com.olivia.core.CaptureHeartbeat
import com.olivia.core.CaptureId
import com.olivia.core.Correction
import com.olivia.core.CorrectionStatus
import com.olivia.core.CorrectionTracker
import com.olivia.core.Direction
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
import kotlinx.coroutines.channels.BufferOverflow
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
    /** Firebase/App Check access is not authorized for this installed build. */
    FIREBASE_CONFIGURATION_UNAVAILABLE,
    ANALYSIS_UNAVAILABLE,
    AUDIO_UNAVAILABLE,
}

/**
 * Latest decoded preview of the live glasses stream.
 *
 * This is deliberately a bounded, in-memory UI projection. It is cleared on pause/stop/close,
 * is never written to the timeline or logs, and must not be confused with the native 3K take.
 */
public class LivePreviewFrame(val frame: Frame) {
    val width: Int get() = frame.width
    val height: Int get() = frame.height
    val sequence: Long get() = frame.stamp.sequence
    val capturedAtMonotonicMs: Long get() = frame.stamp.receivedAt.monotonicMs
    override fun toString(): String = "LivePreviewFrame(${width}x$height, sequence=$sequence)"
}

interface SessionRuntime : AutoCloseable {
    val snapshot: StateFlow<SessionSnapshot?>
    val connection: StateFlow<GlassesConnection>
    val audioStatus: StateFlow<AudioFeedbackStatus>
    val audioStatusMessage: StateFlow<String?>
    /** Aggregate-only, process-local measurements; never contains media, model text or identifiers. */
    val liveLoopMetrics: StateFlow<LiveLoopMetricSnapshot>
    /** Human-readable live-loop phase for the phone UI, not a substitute for audio guidance. */
    val liveGuidanceStatus: StateFlow<LiveGuidanceStatus>
    /** Most recent in-memory stream frame for the recording screen; null outside an active take. */
    val livePreview: StateFlow<LivePreviewFrame?>
    /** Current correction direction for the visual overlay; audio remains the authoritative output. */
    val liveGuidanceDirection: StateFlow<Direction?>
    /** Local aggregate index used by the Sessions screen; no media or frame data is retained. */
    val sessionHistory: StateFlow<List<SessionHistoryEntry>>
    suspend fun handle(command: LocalSessionCommand): Result<SessionSnapshot>
    suspend fun setAudioMode(mode: AudioMode): Result<Unit>
    suspend fun signal(signal: com.olivia.core.SessionSignal): Result<com.olivia.core.ClockReading>
    suspend fun simulateConnection(connected: Boolean): Result<Unit> =
        Result.failure(IllegalStateException("Simulação disponível somente no mock offline"))
    suspend fun timelineFor(sessionId: SessionId): Result<List<TimelineEvent>>
    suspend fun processPostTake(work: suspend (SessionId) -> Result<SessionReview>): Result<SessionReview>
}

/** Variant boundary: mock stays offline; the glasses source set owns the DAT/Firebase wiring. */
interface BackendRuntimeProvider {
    fun create(context: Context): SessionRuntime

    /** Registers variant-owned activity-result contracts before the activity reaches STARTED. */
    fun registerGlassesPermissionLauncher(activity: ComponentActivity): Result<Unit> =
        Result.success(Unit)

    /** Starts the official DAT/Meta AI registration flow for a physical backend. */
    fun startGlassesRegistration(activity: Activity): Result<Unit> =
        Result.failure(UnsupportedOperationException("Registro DAT indisponível nesta variante"))
}

object SessionRuntimeFactory {
    fun create(context: Context, backend: GlassesBackend): SessionRuntime = try {
        val providerClass = Class.forName("com.olivia.companion.VariantRuntimeProvider")
        val provider = providerClass.getField("INSTANCE").get(null) as BackendRuntimeProvider
        provider.create(context.applicationContext)
    } catch (_: Throwable) {
        UnavailableSessionRuntime()
    }

    fun startGlassesRegistration(activity: Activity): Result<Unit> = try {
        val providerClass = Class.forName("com.olivia.companion.VariantRuntimeProvider")
        val provider = providerClass.getField("INSTANCE").get(null) as BackendRuntimeProvider
        provider.startGlassesRegistration(activity)
    } catch (error: Throwable) {
        Result.failure(error)
    }

    fun registerGlassesPermissionLauncher(activity: ComponentActivity): Result<Unit> = try {
        val providerClass = Class.forName("com.olivia.companion.VariantRuntimeProvider")
        val provider = providerClass.getField("INSTANCE").get(null) as BackendRuntimeProvider
        provider.registerGlassesPermissionLauncher(activity)
    } catch (error: Throwable) {
        Result.failure(error)
    }
}

class UnavailableSessionRuntime : SessionRuntime {
    override val connection = MutableStateFlow(GlassesConnection.UNAVAILABLE).asStateFlow()
    override val audioStatus = MutableStateFlow(AudioFeedbackStatus.ERROR).asStateFlow()
    override val audioStatusMessage = MutableStateFlow<String?>("Runtime indisponível; confira a configuração do dispositivo.").asStateFlow()
    override suspend fun signal(signal: com.olivia.core.SessionSignal): Result<com.olivia.core.ClockReading> =
        Result.failure(IllegalStateException("Runtime indisponível"))
    private val state = MutableStateFlow<SessionSnapshot?>(null)
    private val metrics = MutableStateFlow(LiveLoopMetrics().snapshot())
    private val guidance = MutableStateFlow(LiveGuidanceStatus.IDLE)
    private val preview = MutableStateFlow<LivePreviewFrame?>(null)
    private val direction = MutableStateFlow<Direction?>(null)
    override val snapshot: StateFlow<SessionSnapshot?> = state.asStateFlow()
    override val liveLoopMetrics: StateFlow<LiveLoopMetricSnapshot> = metrics.asStateFlow()
    override val liveGuidanceStatus: StateFlow<LiveGuidanceStatus> = guidance.asStateFlow()
    override val livePreview: StateFlow<LivePreviewFrame?> = preview.asStateFlow()
    override val liveGuidanceDirection: StateFlow<Direction?> = direction.asStateFlow()
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
    private val audio = AndroidAudioFeedback(
        context,
        clock,
        AudioMode.VOICE,
        timingSink = AudioTimingSink { stage, durationMs ->
            Log.i(LOG_TAG, "audio_timing stage=$stage ms=$durationMs")
        },
    )
    override val audioStatus = audio.status
    override val audioStatusMessage = audio.statusMessage
    private val _connection = MutableStateFlow(GlassesConnection.UNAVAILABLE)
    override val connection = _connection.asStateFlow()
    private val timeline = FileTimeline(context.filesDir.toPath().resolve("olivia-timeline"))
    private val coordinator = SessionCoordinator(clock, gateway, audio, timeline)
    private val fastProcessor = analyzer as? FastFrameProcessor
    // Camera/tracker cadence is independent; cloud validation remains capped at 0.5 fps.
    private val sampler = FrameSampler(
        clock = clock,
        analysisIntervalMs = 2_000,
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
    private val _livePreview = MutableStateFlow<LivePreviewFrame?>(null)
    override val livePreview: StateFlow<LivePreviewFrame?> = _livePreview.asStateFlow()
    private val _liveGuidanceDirection = MutableStateFlow<Direction?>(null)
    override val liveGuidanceDirection: StateFlow<Direction?> = _liveGuidanceDirection.asStateFlow()
    private val historyStore = LocalSessionHistory(context)
    override val sessionHistory: StateFlow<List<SessionHistoryEntry>> = historyStore.entries.stateIn(
        runtimeScope,
        SharingStarted.Eagerly,
        emptyList(),
    )
    /** Serializes short state transitions; provider, TTS and frame I/O never run under this lock. */
    private val runtimeMutex = Mutex()
    private var lastRecoveryAttemptMs = Long.MIN_VALUE
    // Positive "framed" cue: only after a real deviation, never on every calmly-centered frame.
    private var wasDeviatedSinceFramedCue = false
    private var lastFramedSignalAtMs: Long? = null
    private val analysisFrames = Channel<Frame>(capacity = 1)
    private var activeAnalysisJob: Job? = null
    // Tracking may be slower than the camera. It must never stall preview or accumulate work.
    private val trackingFrames = Channel<Frame>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val trackingWorker: Job = runtimeScope.launch {
        for (frame in trackingFrames) {
            if (frame.stamp.ageMs(clock.now()) > LIVE_FRAME_MAX_AGE_MS ||
                !runtimeMutex.withLock { coordinator.accepts(frame.stamp) }) continue
            val started = clock.now()
            val result = fastProcessor?.processFast(frame)
            if (com.olivia.companion.BuildConfig.DEBUG) Log.i(LOG_TAG,
                "tracking_timing sequence=${frame.stamp.sequence} ms=${clock.now().monotonicMs - started.monotonicMs}")
            // Remote samples correspond to frames actually included in the motion history.
            val decisions = runtimeMutex.withLock { sampler.offer(frame, coordinator::accepts) }
            dispatchDecisions(decisions)
            if (result != null) processFastAnalysis(frame.stamp, result, started)
        }
    }
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
            _connection.value = connection
            runtimeMutex.withLock {
                coordinator.onConnectionChanged(connection)
                expireIfDegraded()
            }
        }
    }
    private val frameObserver: Job = runtimeScope.launch {
        gateway.frames.collect { frame ->
            if (!runtimeMutex.withLock { coordinator.accepts(frame.stamp) }) return@collect
            // Publish raw pixels immediately. GPU rendering has its own latest-frame slot.
            _livePreview.value = LivePreviewFrame(frame)
            if (com.olivia.companion.BuildConfig.DEBUG) Log.i(LOG_TAG,
                "preview_timing sequence=${frame.stamp.sequence} decoded_to_publish_ms=${clock.now().monotonicMs - frame.stamp.receivedAt.monotonicMs}")
            if (frame.stamp.ageMs(clock.now()) > LIVE_FRAME_MAX_AGE_MS) {
                _liveGuidanceDirection.value = null
                _liveGuidanceStatus.value = LiveGuidanceStatus.ANALYSIS_UNAVAILABLE
                return@collect
            }
            if (fastProcessor != null) {
                trackingFrames.trySend(frame)
                runtimeMutex.withLock { metrics.onFrameReceived(); publishMetrics() }
                return@collect
            }
            val decisions = runtimeMutex.withLock {
                metrics.onFrameReceived()
                publishMetrics()
                sampler.offer(frame, coordinator::accepts)
            }
            dispatchDecisions(decisions)
        }
    }
    private val watchdog: Job = runtimeScope.launch {
        while (isActive) {
            runtimeMutex.withLock {
                coordinator.checkCaptureHealth()
                expireIfDegraded()
                val current = coordinator.snapshot.value
                if (current != null && com.olivia.core.DegradationCause.CAPTURE_UNAVAILABLE in current.degradationCauses &&
                    (lastRecoveryAttemptMs == Long.MIN_VALUE || clock.now().monotonicMs - lastRecoveryAttemptMs >= 4_000)
                ) {
                    lastRecoveryAttemptMs = clock.now().monotonicMs
                    coordinator.retryCapture()
                }
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
                    expirePendingCorrection()
                    guidance = createGuidance(audioMode)
                    lastRecoveryAttemptMs = Long.MIN_VALUE
                    resetFramedSignalState()
                    (analyzer as? HybridVisualAnalyzer)?.reset()
                    _livePreview.value = null
                    _liveGuidanceDirection.value = null
                    _liveGuidanceStatus.value = LiveGuidanceStatus.LOCATING_TARGET
                    coordinator.start(
                        sessionId = SessionId(UUID.randomUUID().toString()),
                        config = SessionConfig(audioMode = audioMode),
                    )
                }
                LocalSessionCommand.PAUSE -> {
                    resetFramedSignalState()
                    (analyzer as? HybridVisualAnalyzer)?.reset()
                    _livePreview.value = null
                    _liveGuidanceDirection.value = null
                    coordinator.pause()
                }
                LocalSessionCommand.RESUME -> {
                    resetFramedSignalState()
                    (analyzer as? HybridVisualAnalyzer)?.reset()
                    _livePreview.value = null
                    _liveGuidanceDirection.value = null
                    coordinator.resume()
                }
                LocalSessionCommand.STOP -> {
                    resetFramedSignalState()
                    (analyzer as? HybridVisualAnalyzer)?.reset()
                    _livePreview.value = null
                    _liveGuidanceDirection.value = null
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
        if (coordinator.snapshot.value?.id != sessionId && sessionHistory.value.none { it.sessionId == sessionId.value }) {
            Result.failure(IllegalArgumentException("Timeline fora da sessão ativa"))
        } else {
            Result.success(timeline.events(sessionId))
        }
    }

    override suspend fun signal(signal: com.olivia.core.SessionSignal): Result<com.olivia.core.ClockReading> = audio.signal(signal)

    override suspend fun simulateConnection(connected: Boolean): Result<Unit> = runtimeMutex.withLock {
        val mock = gateway as? ScenarioGlassesGateway
            ?: return@withLock Result.failure(IllegalStateException("Simulação disponível somente no mock offline"))
        mock.setConnected(connected)
        Result.success(Unit)
    }

    override suspend fun processPostTake(work: suspend (SessionId) -> Result<SessionReview>): Result<SessionReview> {
        val started = runtimeMutex.withLock { coordinator.beginPostProcessing() }
        val sessionId = started.getOrElse { return Result.failure(it) }.id
        return try {
            work(sessionId).also { result ->
                runtimeMutex.withLock {
                    if (result.isSuccess) coordinator.completePostProcessing(sessionId)
                    else coordinator.cancelPostProcessing(sessionId)
                }
            }
        } catch (error: CancellationException) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                runtimeMutex.withLock { coordinator.cancelPostProcessing(sessionId) }
            }
            throw error
        } catch (error: Exception) {
            runtimeMutex.withLock { coordinator.cancelPostProcessing(sessionId) }
            Result.failure(IllegalStateException("Pós-processamento indisponível"))
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
                if (fastProcessor != null && (analyzer as? HybridVisualAnalyzer)?.currentTrackedRoi() != null) return
                _liveGuidanceStatus.value = when ((result.exceptionOrNull() as? AnalysisFailure)?.code) {
                    com.olivia.core.AnalysisFailureCode.CONFIGURATION ->
                        LiveGuidanceStatus.FIREBASE_CONFIGURATION_UNAVAILABLE
                    else -> LiveGuidanceStatus.ANALYSIS_UNAVAILABLE
                }
                runtimeMutex.withLock { coordinator.onAnalysisUnavailable() }
                return
            }
            // Remote localization already reconciles into the live tracker. A late cloud
            // diagnosis must not speak about the original (possibly seconds-old) image.
            if (fastProcessor != null) return
            val analysis = result.getOrThrow()
            if (analysis.frameStamp.ageMs(clock.now()) > LIVE_FRAME_MAX_AGE_MS) return
            val hasTrackedTarget = (analyzer as? HybridVisualAnalyzer)?.currentTrackedRoi() != null
            _liveGuidanceStatus.value = if (!hasTrackedTarget && analyzer is HybridVisualAnalyzer) {
                LiveGuidanceStatus.TARGET_NOT_FOUND
            } else if (analysis.hasDeviation) {
                LiveGuidanceStatus.TARGET_FOUND
            } else {
                LiveGuidanceStatus.TARGET_CENTERED
            }
            var correction: Correction? = null
            var justFramed = false
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
                    if (analysis.hasDeviation) {
                        wasDeviatedSinceFramedCue = true
                    } else {
                        justFramed = maybeArmFramedSignal()
                    }
                    if (correction != null) {
                        _liveGuidanceDirection.value = correction?.direction
                    } else if (!analysis.hasDeviation) {
                        _liveGuidanceDirection.value = null
                    }
                    true
                }
            }
            if (stillAccepted && correction != null) {
                val issuedCorrection = requireNotNull(correction)
                _liveGuidanceStatus.value = LiveGuidanceStatus.GUIDANCE_PENDING
                // Diagnostic-only: no pixels/ROI/user data. Lets a device trial tell apart a fast
                // local (tracker/OpenCV) correction from a slow Gemini round-trip one.
                Log.i(
                    LOG_TAG,
                    "correction_dispatch source=${analysis.source} kind=${issuedCorrection.diagnosis.kind} " +
                        "direction=${issuedCorrection.direction} frame_age_ms=" +
                        "${clock.now().monotonicMs - frame.stamp.receivedAt.monotonicMs}",
                )
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
            } else if (stillAccepted && justFramed) {
                runCatching { audio.signal(com.olivia.core.SessionSignal.TARGET_FRAMED) }
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
            if (analysis.source == com.olivia.core.AnalyzerSource.LOCAL_ROI_LOCAL &&
                com.olivia.companion.BuildConfig.DEBUG
            ) {
                // Diagnostic-only: source, sequence and local processing time contain no pixels,
                // ROI coordinates, model response or user data. This is emitted once per local
                // first-acquisition attempt and lets the device trial distinguish it from Gemini.
                Log.i(
                    LOG_TAG,
                    "local ROI proposal accepted sequence=${analysis.frameStamp.sequence} latency_ms=${analysis.latencyMs}",
                )
            }
            var correction: Correction? = null
            var justFramed = false
            val accepted = runtimeMutex.withLock {
                if (!coordinator.accepts(analysis.frameStamp) || analysis.frameStamp.ageMs(clock.now()) > LIVE_FRAME_MAX_AGE_MS) false
                else {
                    coordinator.onAnalysisRestored()
                    coordinator.recordDiagnosis(analysis)
                    correctionTracker.onAnalysis(analysis, clock.now())?.let { outcome ->
                        guidance.onCorrectionOutcome(outcome.correction.id, outcome.status)
                        coordinator.recordCorrectionOutcome(outcome)
                    }
                    correction = guidance.onAnalysis(analysis, clock.now())
                    if (analysis.hasDeviation) {
                        wasDeviatedSinceFramedCue = true
                    } else {
                        justFramed = maybeArmFramedSignal()
                    }
                    if (correction != null) {
                        _liveGuidanceDirection.value = correction?.direction
                    } else if (!analysis.hasDeviation) {
                        _liveGuidanceDirection.value = null
                    }
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
                // Diagnostic-only: no pixels/ROI/user data. Lets a device trial tell apart a fast
                // local (tracker/OpenCV) correction from a slow Gemini round-trip one.
                Log.i(
                    LOG_TAG,
                    "correction_dispatch source=${analysis.source} kind=${issued.diagnosis.kind} " +
                        "direction=${issued.direction} frame_age_ms=" +
                        "${clock.now().monotonicMs - stamp.receivedAt.monotonicMs}",
                )
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
            } else if (accepted && justFramed) {
                runCatching { audio.signal(com.olivia.core.SessionSignal.TARGET_FRAMED) }
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

    private fun resetFramedSignalState() {
        wasDeviatedSinceFramedCue = false
        lastFramedSignalAtMs = null
    }

    /**
     * Arms the positive "framed" tone only after a real deviation was observed, and only once
     * per [FRAMED_SIGNAL_COOLDOWN_MS], so a calmly-centered take never gets a beep every sample.
     * Must be called while holding [runtimeMutex]; the caller plays the tone outside the lock.
     */
    private fun maybeArmFramedSignal(): Boolean {
        if (!wasDeviatedSinceFramedCue) return false
        val now = clock.now().monotonicMs
        val last = lastFramedSignalAtMs
        if (last != null && now - last < FRAMED_SIGNAL_COOLDOWN_MS) return false
        wasDeviatedSinceFramedCue = false
        lastFramedSignalAtMs = now
        return true
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
        trackingFrames.close()
        analysisWorker.cancel()
        trackingWorker.cancel()
        _livePreview.value = null
        _liveGuidanceDirection.value = null
        (gateway as? AutoCloseable)?.close()
        audio.close()
        runtimeScope.cancel()
    }
}

private const val LOG_TAG = "OliviaSessionRuntime"
private const val LIVE_FRAME_MAX_AGE_MS = 750L
private const val FRAMED_SIGNAL_COOLDOWN_MS = 4_000L

fun createMockSessionRuntime(context: Context): SessionRuntime {
    val clock = SystemOliviaClock()
    return LiveSessionRuntime(context, ScenarioGlassesGateway(clock), ScenarioFrameAnalyzer(clock), clock)
}
