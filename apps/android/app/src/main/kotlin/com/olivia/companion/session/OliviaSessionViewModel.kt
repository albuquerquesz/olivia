package com.olivia.companion.session

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.olivia.companion.ActiveBackend
import com.olivia.companion.GlassesBackend
import com.olivia.core.AudioMode
import com.olivia.core.Correction
import com.olivia.core.CorrectionStatus
import com.olivia.core.DiagnosisKind
import com.olivia.core.FrameAnalysis
import com.olivia.core.FrameAnalyzer
import com.olivia.core.GlassesConnection
import com.olivia.core.GlassesGateway
import com.olivia.core.PostProcessor
import com.olivia.core.RecordingAsset
import com.olivia.core.RecordingSource
import com.olivia.core.SessionConfig
import com.olivia.core.SessionId
import com.olivia.core.SessionReview
import com.olivia.core.SessionSnapshot
import com.olivia.core.SessionState
import com.olivia.core.SessionSignal
import com.olivia.core.TimelineEvent
import com.olivia.core.TimelineEventType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

private val liveSessionStates = setOf(
    SessionState.STARTED,
    SessionState.PAUSED,
    SessionState.DEGRADED,
)

data class OliviaUiState(
    val backend: GlassesBackend,
    val audioMode: AudioMode,
    val session: SessionSnapshot?,
    val connection: GlassesConnection,
    val elapsedMs: Long,
    val takes: List<TakeSummary>,
    val voiceState: VoiceCommandState,
    val audioStatus: AudioFeedbackStatus = AudioFeedbackStatus.INITIALIZING,
    val audioStatusMessage: String? = null,
    val exportState: ExportState = ExportState(),
) {
    val isLive: Boolean
        get() = session?.state in setOf(
            SessionState.STARTED,
            SessionState.PAUSED,
            SessionState.DEGRADED,
        )
}

class OliviaSessionViewModel(
    private val audioModeStore: AudioModeRepository,
    backend: GlassesBackend,
    private val glassesGateway: GlassesGateway?,
    private val frameAnalyzer: FrameAnalyzer?,
    private val postProcessor: PostProcessor,
    private val audioFeedback: AudioFeedbackController,
    private val voiceCommandSource: VoiceCommandSource? = null,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val recordingSource: RecordingSource? = null,
    private val exportAdapter: SessionExportAdapter? = null,
    private val initialTakes: List<TakeSummary> = emptyList(),
) : ViewModel() {
    private val timeline = InMemoryTimeline()
    private val runtime = SessionRuntime(timeline, clock)
    private val guidance = ThresholdGuidanceEngine(SessionConfig().correctionCooldownMs)
    private var frameJob: Job? = null
    private var gatewayCommand: Job? = null
    private var ticker: Job? = null
    private var watchdog: Job? = null
    private var voiceCommandJob: Job? = null
    private var connectionJob: Job? = null
    private var audioStatusJob: Job? = null
    private var pendingCorrection: Correction? = null
    private val reviews = mutableMapOf<String, SessionReview>()
    private val seededTakes = initialTakes
    private var latestConnection = initialConnection(backend, null)

    private val _uiState = MutableStateFlow(
        OliviaUiState(
            backend = backend,
            audioMode = audioModeStore.current(),
            session = null,
            connection = initialConnection(backend, null),
            elapsedMs = 0,
            takes = seededTakes,
            voiceState = voiceCommandSource?.state?.value ?: VoiceCommandState.UNAVAILABLE,
            audioStatus = audioFeedback.status.value,
            audioStatusMessage = audioFeedback.statusMessage.value,
        ),
    )
    val uiState: StateFlow<OliviaUiState> = _uiState.asStateFlow()

    init {
        glassesGateway?.let { gateway ->
            connectionJob = viewModelScope.launch {
                gateway.connection.collect { connection ->
                    latestConnection = connection
                    publish()
                }
            }
        }
        frameJob = glassesGateway?.let { gateway ->
            viewModelScope.launch {
                gateway.frames.collect { frame ->
                    if (!runtime.canProcessFrame(frame.sessionId, frame.capturedAtEpochMs)) return@collect
                    val wasDegraded = runtime.snapshot?.state == SessionState.DEGRADED
                    frameAnalyzer?.analyze(frame)?.getOrNull()
                        ?.takeIf { it.sessionId == frame.sessionId }
                        ?.let { analysis ->
                            if (!runtime.canProcessFrame(frame.sessionId, frame.capturedAtEpochMs)) return@collect
                            handleAnalysis(analysis)
                        }
                    if (!runtime.canProcessFrame(frame.sessionId, frame.capturedAtEpochMs)) return@collect
                    runtime.frameReceived(frame.capturedAtEpochMs)
                    if (wasDegraded) signalAudio(SessionSignal.CONNECTION_RESTORED)
                    publish()
                }
            }
        }
        audioStatusJob = viewModelScope.launch {
            audioFeedback.status.collect {
                publish()
            }
        }
        voiceCommandSource?.let { source ->
            voiceCommandJob = viewModelScope.launch {
                launch {
                    source.commands.collect(::handleVoiceCommand)
                }
                launch {
                    source.errors.collect(::handleVoiceCommandError)
                }
                source.state.collect {
                    publish()
                }
            }
        }
        publish()
    }

    fun startVoiceCommands() {
        if (voiceCommandSource?.state?.value == VoiceCommandState.UNAVAILABLE) return
        voiceCommandSource?.start()
        publish()
    }

    fun stopVoiceCommands() {
        voiceCommandSource?.stop()
        publish()
    }

    fun setAudioMode(mode: AudioMode) {
        if (_uiState.value.isLive) return
        audioModeStore.set(mode)
        publish()
    }

    fun start() {
        val config = SessionConfig(audioMode = _uiState.value.audioMode)
        val canStart = runtime.snapshot?.state == null || runtime.snapshot?.state in setOf(
            SessionState.IDLE,
            SessionState.STOPPED,
        )
        if (!canStart) {
            publish()
            return
        }
        val started = runtime.start(config)
        if (started != null) {
            guidance.reset()
            pendingCorrection = null
            startGateway(started.id, config)
            signalAudio(SessionSignal.SESSION_STARTED)
        }
        startTicker()
        startWatchdog()
        publish()
    }

    fun pause() {
        val wasLive = runtime.snapshot?.state in setOf(SessionState.STARTED, SessionState.DEGRADED)
        val paused = runtime.pause()
        if (wasLive && paused?.state == SessionState.PAUSED) {
            ticker?.cancel()
            ticker = null
            watchdog?.cancel()
            watchdog = null
            expirePendingCorrection(paused.id, clock())
            gatewayCommand?.cancel()
            gatewayCommand = glassesGateway?.let { gateway ->
                viewModelScope.launch { gateway.pauseCapture() }
            }
            signalAudio(SessionSignal.SESSION_PAUSED)
        }
        publish()
    }

    fun resume() {
        val wasPaused = runtime.snapshot?.state == SessionState.PAUSED
        val resumed = runtime.resume()
        if (wasPaused && resumed?.state == SessionState.STARTED) {
            gatewayCommand?.cancel()
            gatewayCommand = glassesGateway?.let { gateway ->
                viewModelScope.launch { gateway.resumeCapture() }
            }
            signalAudio(SessionSignal.SESSION_RESUMED)
        }
        startTicker()
        startWatchdog()
        publish()
    }

    fun degrade() {
        val degraded = runtime.degrade()
        if (degraded?.state == SessionState.DEGRADED) {
            expirePendingCorrection(degraded.id, clock())
            signalAudio(SessionSignal.CONNECTION_LOST)
            requestGatewayRecovery()
        }
        publish()
    }

    fun recover() {
        val wasDegraded = runtime.snapshot?.state == SessionState.DEGRADED
        val recovered = runtime.recover()
        if (wasDegraded && recovered?.state == SessionState.STARTED) {
            signalAudio(SessionSignal.CONNECTION_RESTORED)
        }
        if (wasDegraded) requestGatewayRecovery()
        startTicker()
        publish()
    }

    fun stop() {
        val shouldSignal = runtime.snapshot?.state in setOf(
            SessionState.STARTED,
            SessionState.PAUSED,
            SessionState.DEGRADED,
        )
        runtime.snapshot?.id?.let { sessionId -> expirePendingCorrection(sessionId, clock()) }
        val stopped = runtime.stop()
        if (shouldSignal && stopped?.state == SessionState.STOPPED) signalAudio(SessionSignal.SESSION_STOPPED)
        if (shouldSignal && stopped?.state == SessionState.STOPPED) processTake(stopped.id)
        ticker?.cancel()
        ticker = null
        watchdog?.cancel()
        watchdog = null
        gatewayCommand?.cancel()
        gatewayCommand = glassesGateway?.let { gateway ->
            viewModelScope.launch { gateway.stopCapture() }
        }
        publish()
    }

    fun cancel() {
        val sessionId = runtime.cancel() ?: return
        timeline.remove(sessionId)
        reviews.remove(sessionId.value)
        pendingCorrection = null
        guidance.reset()
        ticker?.cancel()
        ticker = null
        watchdog?.cancel()
        watchdog = null
        gatewayCommand?.cancel()
        gatewayCommand = glassesGateway?.let { gateway ->
            viewModelScope.launch { gateway.stopCapture() }
        }
        publish()
    }

    fun take(sessionId: SessionId): TakeSummary? =
        _uiState.value.takes.firstOrNull { it.sessionId == sessionId }

    suspend fun localRecordingPath(asset: RecordingAsset): String? = try {
        recordingSource?.localPathOf(asset.id)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        null
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = viewModelScope.launch {
            while (isActive) {
                delay(250)
                val state = runtime.snapshot?.state
                if (state != SessionState.STARTED && state != SessionState.DEGRADED) break
                publish()
            }
        }
    }

    private fun startGateway(sessionId: SessionId, config: SessionConfig) {
        gatewayCommand?.cancel()
        gatewayCommand = glassesGateway?.let { gateway ->
            viewModelScope.launch { gateway.startCapture(sessionId, config) }
        }
    }

    private suspend fun handleAnalysis(analysis: FrameAnalysis) {
        recordDiagnoses(analysis)
        val pending = pendingCorrection
        if (pending != null && !analysis.hasDeviation) {
            guidance.onCorrectionOutcome(pending.id, CorrectionStatus.OBEYED)
            recordCorrectionOutcome(pending, CorrectionStatus.OBEYED, analysis.analyzedAtEpochMs)
            pendingCorrection = null
        }
        guidance.onAnalysis(analysis, analysis.analyzedAtEpochMs)?.let { correction ->
            pendingCorrection?.let { previous ->
                guidance.onCorrectionOutcome(previous.id, CorrectionStatus.SUPERSEDED)
                recordCorrectionOutcome(previous, CorrectionStatus.SUPERSEDED, correction.issuedAtEpochMs)
            }
            pendingCorrection = correction
            recordCorrectionIssued(correction)
            audioFeedback.announce(correction)
        }
    }

    private fun recordDiagnoses(analysis: FrameAnalysis) {
        analysis.diagnoses.forEach { diagnosis ->
            timeline.record(
                TimelineEvent(
                    id = "${analysis.sessionId.value}-diagnosis-${analysis.frameCapturedAtEpochMs}-${diagnosis.kind}",
                    sessionId = analysis.sessionId,
                    atEpochMs = analysis.analyzedAtEpochMs,
                    offsetMs = sessionOffset(analysis.frameCapturedAtEpochMs),
                    type = TimelineEventType.DIAGNOSIS,
                    diagnosis = diagnosis,
                    direction = diagnosis.direction,
                    confidence = diagnosis.confidence,
                    label = diagnosisLabel(diagnosis.kind),
                ),
            )
        }
    }

    private fun recordCorrectionIssued(correction: Correction) {
        timeline.record(
            TimelineEvent(
                id = "${correction.sessionId.value}-correction-${correction.id}",
                sessionId = correction.sessionId,
                atEpochMs = correction.issuedAtEpochMs,
                offsetMs = sessionOffset(correction.issuedAtEpochMs),
                type = TimelineEventType.CORRECTION_ISSUED,
                diagnosis = correction.diagnosis,
                direction = correction.direction,
                confidence = correction.confidence,
                correctionStatus = CorrectionStatus.PENDING,
                correctionId = correction.id,
                label = correction.spokenText,
            ),
        )
    }

    private fun recordCorrectionOutcome(
        correction: Correction,
        status: CorrectionStatus,
        atEpochMs: Long,
    ) {
        timeline.record(
            TimelineEvent(
                id = "${correction.sessionId.value}-outcome-${correction.id}-$status",
                sessionId = correction.sessionId,
                atEpochMs = atEpochMs,
                offsetMs = sessionOffset(atEpochMs),
                type = TimelineEventType.CORRECTION_RESOLVED,
                diagnosis = correction.diagnosis,
                direction = correction.direction,
                confidence = correction.confidence,
                correctionStatus = status,
                correctionId = correction.id,
                label = correctionOutcomeLabel(status),
            ),
        )
    }

    private fun expirePendingCorrection(sessionId: SessionId, atEpochMs: Long) {
        val pending = pendingCorrection ?: return
        if (pending.sessionId != sessionId) return
        guidance.onCorrectionOutcome(pending.id, CorrectionStatus.EXPIRED)
        recordCorrectionOutcome(pending, CorrectionStatus.EXPIRED, atEpochMs)
        pendingCorrection = null
    }

    private fun signalAudio(signal: SessionSignal) {
        viewModelScope.launch { audioFeedback.signal(signal) }
    }

    private fun handleVoiceCommand(command: VoiceCommand) {
        val stateBefore = runtime.snapshot?.state
        when (command) {
            VoiceCommand.START -> start()
            VoiceCommand.PAUSE -> pause()
            VoiceCommand.RESUME -> resume()
            VoiceCommand.STOP -> stop()
        }
        if (runtime.snapshot?.state == stateBefore) {
            signalAudio(SessionSignal.VOICE_COMMAND_ERROR)
        }
    }

    private fun handleVoiceCommandError(error: VoiceCommandError) {
        when (error) {
            VoiceCommandError.COMMAND_NOT_RECOGNIZED,
            VoiceCommandError.ENGINE_ERROR -> signalAudio(SessionSignal.VOICE_COMMAND_ERROR)
            VoiceCommandError.INPUT_UNAVAILABLE -> signalAudio(SessionSignal.VOICE_INPUT_UNAVAILABLE)
        }
    }

    private fun sessionOffset(atEpochMs: Long): Long =
        (atEpochMs - (runtime.snapshot?.startedAtEpochMs ?: atEpochMs)).coerceAtLeast(0)

    private fun diagnosisLabel(kind: DiagnosisKind): String = when (kind) {
        DiagnosisKind.FRAMING -> "Desvio de enquadramento"
        DiagnosisKind.LIGHT -> "Sombra ou baixa luz"
        DiagnosisKind.HORIZON -> "Horizonte inclinado"
    }

    private fun correctionOutcomeLabel(status: CorrectionStatus): String = when (status) {
        CorrectionStatus.OBEYED -> "Correção confirmada"
        CorrectionStatus.PENDING -> "Correção pendente"
        CorrectionStatus.IGNORED -> "Correção não confirmada"
        CorrectionStatus.EXPIRED -> "Correção expirada"
        CorrectionStatus.SUPERSEDED -> "Correção substituída"
    }

    private fun startWatchdog() {
        if (watchdog?.isActive == true) return
        watchdog = viewModelScope.launch {
            while (isActive) {
                delay(250)
                val session = runtime.snapshot ?: break
                if (session.state != SessionState.STARTED && session.state != SessionState.DEGRADED) {
                    break
                }
                if (runtime.shouldDegrade(clock())) {
                    val degraded = runtime.degrade()
                    if (degraded?.state == SessionState.DEGRADED) {
                        expirePendingCorrection(degraded.id, clock())
                        signalAudio(SessionSignal.CONNECTION_LOST)
                        requestGatewayRecovery()
                        publish()
                    }
                }
            }
        }
    }

    private fun publish() {
        val session = runtime.view()
        val audioMode = audioModeStore.current()
        _uiState.update {
            it.copy(
                audioMode = audioMode,
                session = session,
                connection = currentConnection(it.backend, session),
                elapsedMs = session?.elapsedMs ?: 0,
                takes = buildTakes(),
                voiceState = voiceCommandSource?.state?.value ?: VoiceCommandState.UNAVAILABLE,
                audioStatus = audioFeedback.status.value,
                audioStatusMessage = audioFeedback.statusMessage.value,
            )
        }
    }

    private fun buildTakes(): List<TakeSummary> {
        val liveSessionId = runtime.snapshot
            ?.takeIf { it.state in liveSessionStates }
            ?.id
        return (seededTakes + timeline.sessionIds()
            .mapIndexedNotNull { index, sessionId ->
                val events = timeline.events(sessionId)
                if (events.isEmpty()) return@mapIndexedNotNull null
                takeFromEvents(
                    sessionId = sessionId,
                    title = if (sessionId == liveSessionId) "Take ao vivo" else "Take ${index + 1}",
                    events = events,
                    review = reviews[sessionId.value],
                )
            }
        ).sortedByDescending { it.recordedAtEpochMs }
    }

    private fun processTake(sessionId: SessionId) {
        val events = timeline.events(sessionId)
        viewModelScope.launch {
            val recording = recordingSource?.let { source ->
                try {
                    val asset = source.listTakes(sessionId).firstOrNull()
                    if (asset != null) {
                        source.fetch(asset).collect { }
                    }
                    asset
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    null
                }
            }
            postProcessor.process(
                sessionId = sessionId,
                recording = recording,
                timeline = events,
            ).getOrNull()?.let { review ->
                timeline.replace(sessionId, review.timeline)
                reviews[sessionId.value] = review
                publish()
            }
        }
    }

    fun exportTake(sessionId: SessionId) {
        val take = take(sessionId)
        val review = take?.review
        val recording = review?.recording
        val source = recordingSource
        val adapter = exportAdapter
        if (review == null || recording == null || source == null || adapter == null) {
            _uiState.update {
                it.copy(
                    exportState = ExportState(
                        status = ExportStatus.UNAVAILABLE,
                        message = "O vídeo nativo ainda não está disponível neste ambiente.",
                    ),
                )
            }
            return
        }
        _uiState.update { it.copy(exportState = ExportState(status = ExportStatus.SHARING)) }
        viewModelScope.launch {
            val path = try {
                source.localPathOf(recording.id)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                null
            }
            val result = adapter.share(review, path)
            _uiState.update { it.copy(exportState = result) }
        }
    }

    private fun requestGatewayRecovery() {
        gatewayCommand?.cancel()
        gatewayCommand = glassesGateway?.let { gateway ->
            viewModelScope.launch { gateway.resumeCapture() }
        }
    }

    private fun currentConnection(
        backend: GlassesBackend,
        session: SessionSnapshot?,
    ): GlassesConnection {
        if (session?.state == SessionState.DEGRADED) return GlassesConnection.DISCONNECTED
        return latestConnection.takeIf { glassesGateway != null }
            ?: initialConnection(backend, session)
    }

    private fun initialConnection(
        backend: GlassesBackend,
        session: SessionSnapshot?,
    ): GlassesConnection {
        if (session?.state == SessionState.DEGRADED) return GlassesConnection.DISCONNECTED
        if (backend.requiresPhysicalGlasses) return GlassesConnection.UNAVAILABLE
        return GlassesConnection.CONNECTED
    }

    override fun onCleared() {
        audioFeedback.close()
        if (glassesGateway is AutoCloseable) glassesGateway.close()
        frameJob?.cancel()
        connectionJob?.cancel()
        audioStatusJob?.cancel()
        gatewayCommand?.cancel()
        watchdog?.cancel()
        ticker?.cancel()
        voiceCommandSource?.close()
        voiceCommandJob?.cancel()
        super.onCleared()
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val audioModeStore = AudioModeStore(application)
            return OliviaSessionViewModel(
                audioModeStore = audioModeStore,
                backend = ActiveBackend.current,
                glassesGateway = ActiveBackend.createGlassesGateway(),
                frameAnalyzer = ActiveBackend.createFrameAnalyzer(),
                postProcessor = ActiveBackend.createPostProcessor(),
                recordingSource = ActiveBackend.createRecordingSource(application),
                exportAdapter = AndroidSessionExportAdapter(application),
                initialTakes = seedTakes().takeIf { ActiveBackend.current.id == "mock" }.orEmpty(),
                audioFeedback = AndroidAudioFeedback(
                    context = application,
                    modeProvider = audioModeStore::current,
                ),
                voiceCommandSource = AndroidVoiceCommandSourceFactory.create(application),
            ) as T
        }
    }
}
