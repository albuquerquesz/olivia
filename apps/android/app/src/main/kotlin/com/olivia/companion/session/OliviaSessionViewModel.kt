package com.olivia.companion.session

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.olivia.companion.ActiveBackend
import com.olivia.companion.GlassesBackend
import com.olivia.companion.OliviaPreferences
import com.olivia.core.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val busyStates = setOf(
    SessionState.STARTING, SessionState.STARTED, SessionState.PAUSED,
    SessionState.DEGRADED, SessionState.STOPPING, SessionState.PROCESSING,
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
    val guidanceStatus: LiveGuidanceStatus = LiveGuidanceStatus.IDLE,
    val livePreview: LivePreviewFrame? = null,
    val guidanceDirection: Direction? = null,
    val commandError: String? = null,
    val working: Boolean = false,
) {
    val isLive: Boolean get() = session?.state in busyStates || working
}

/** Presentation only: capture, health, analysis and audio belong to SessionRuntime. */
class OliviaSessionViewModel(
    private val runtime: SessionRuntime,
    backend: GlassesBackend,
    audioModes: Flow<AudioMode>,
    private val saveAudioMode: suspend (AudioMode) -> Unit,
    private val postTake: PostTakeController,
    voiceCommandSource: VoiceCommandSource? = null,
    private val voiceCommandSourceFactory: (() -> VoiceCommandSource)? = null,
    private val initialTakes: List<TakeSummary> = emptyList(),
    private val recordingSource: RecordingSource? = null,
) : ViewModel() {
    private var activeVoiceCommandSource: VoiceCommandSource? = voiceCommandSource
    private val commands = Mutex()
    private val refresh = Mutex()
    private val preferencesReady = CompletableDeferred<Unit>()
    private val reviews = mutableMapOf<SessionId, SessionReview>()
    private val seededTakes = initialTakes
    private val _uiState = MutableStateFlow(OliviaUiState(
        backend, AudioMode.VOICE, runtime.snapshot.value, runtime.connection.value,
        0, initialTakes, activeVoiceCommandSource?.state?.value ?: VoiceCommandState.UNAVAILABLE,
    ))
    private val _voiceNavigation = MutableSharedFlow<LocalSessionCommand>(extraBufferCapacity = 8)
    val uiState: StateFlow<OliviaUiState> = _uiState.asStateFlow()
    val voiceNavigation: Flow<LocalSessionCommand> = _voiceNavigation.asSharedFlow()

    init {
        viewModelScope.launch {
            audioModes.catch { emit(AudioMode.VOICE) }.collect { mode ->
                commands.withLock {
                    if (runtime.setAudioMode(mode).isSuccess) _uiState.update { it.copy(audioMode = mode) }
                    preferencesReady.complete(Unit)
                }
            }
        }
        viewModelScope.launch {
            runtime.snapshot.collect { snapshot ->
                val previous = _uiState.value.session
                _uiState.update { it.copy(session = snapshot, elapsedMs = snapshot?.elapsedMs ?: 0) }
                if (previous?.id != snapshot?.id || previous?.state != snapshot?.state) refreshTakes()
            }
        }
        viewModelScope.launch { runtime.connection.collect { value -> _uiState.update { it.copy(connection = value) } } }
        viewModelScope.launch { runtime.liveGuidanceStatus.collect { value -> _uiState.update { it.copy(guidanceStatus = value) } } }
        viewModelScope.launch { runtime.livePreview.collect { value -> _uiState.update { it.copy(livePreview = value) } } }
        viewModelScope.launch {
            runtime.liveGuidanceDirection.collect { value -> _uiState.update { it.copy(guidanceDirection = value) } }
        }
        viewModelScope.launch { runtime.audioStatus.collect { value -> _uiState.update { it.copy(audioStatus = value) } } }
        viewModelScope.launch { runtime.audioStatusMessage.collect { value -> _uiState.update { it.copy(audioStatusMessage = value) } } }
        viewModelScope.launch { runtime.sessionHistory.collect { refreshTakes() } }
        viewModelScope.launch {
            while (isActive) {
                delay(2_000)
                if (_uiState.value.isLive) refreshTakes()
            }
        }
        activeVoiceCommandSource?.let(::observeVoiceCommandSource)
    }

    /** Recreates an unavailable source after the user installs an offline speech provider. */
    fun startVoiceCommands() {
        val current = activeVoiceCommandSource
        val factory = voiceCommandSourceFactory
        if (current?.state?.value == VoiceCommandState.UNAVAILABLE && factory != null) {
            val replacement = runCatching { factory.invoke() }.getOrNull()
            if (replacement != null) {
                current.close()
                activeVoiceCommandSource = replacement
                observeVoiceCommandSource(replacement)
            }
        }
        activeVoiceCommandSource?.start()
    }

    fun stopVoiceCommands() { activeVoiceCommandSource?.stop() }
    fun start() = dispatch(LocalSessionCommand.START)
    fun pause() = dispatch(LocalSessionCommand.PAUSE)
    fun resume() = dispatch(LocalSessionCommand.RESUME)
    fun stop() = dispatch(LocalSessionCommand.STOP)

    private fun dispatch(command: LocalSessionCommand, fromVoice: Boolean = false) {
        launchOperation {
            val state = runtime.snapshot.value?.state
            val allowed = when (command) {
                LocalSessionCommand.START -> state == null || state in setOf(SessionState.IDLE, SessionState.STOPPED, SessionState.READY, SessionState.ERROR)
                LocalSessionCommand.PAUSE -> state in setOf(SessionState.STARTED, SessionState.DEGRADED)
                LocalSessionCommand.RESUME -> state == SessionState.PAUSED
                LocalSessionCommand.STOP -> state in setOf(SessionState.STARTING, SessionState.STARTED, SessionState.PAUSED, SessionState.DEGRADED)
            }
            if (!allowed) {
                if (fromVoice) runtime.signal(SessionSignal.VOICE_COMMAND_ERROR)
                error("Comando indisponível neste estado da sessão.")
            }
            // Show recording/connection progress before waiting for DAT or the Meta AI prompt.
            if (fromVoice && command != LocalSessionCommand.STOP) _voiceNavigation.tryEmit(command)
            runtime.handle(command).getOrThrow()
            if (fromVoice && command == LocalSessionCommand.STOP) _voiceNavigation.tryEmit(command)
            if (command == LocalSessionCommand.STOP) {
                val stopped = requireNotNull(runtime.snapshot.value)
                val events = runtime.timelineFor(stopped.id).getOrThrow()
                if (postTake.hasDemoRecording) {
                    val review = runtime.processPostTake { postTake.prepareDemo(stopped, events) }.getOrThrow()
                    reviews[stopped.id] = review
                }
                refreshTakes()
            }
        }
    }

    fun setAudioMode(mode: AudioMode) {
        if (_uiState.value.isLive) return
        launchOperation {
            runtime.setAudioMode(mode).getOrThrow()
            saveAudioMode(mode)
            _uiState.update { it.copy(audioMode = mode) }
        }
    }

    fun degrade() = simulateConnection(false)
    fun recover() = simulateConnection(true)
    private fun simulateConnection(connected: Boolean) = launchOperation {
        runtime.simulateConnection(connected).getOrThrow()
    }

    fun take(sessionId: SessionId): TakeSummary? = _uiState.value.takes.firstOrNull { it.sessionId == sessionId }

    suspend fun localRecordingPath(asset: RecordingAsset): String? = try {
        recordingSource?.localPathOf(asset.id)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    fun importTake(sessionId: SessionId, uri: String) = launchOperation {
        val stopped = runtime.snapshot.value?.takeIf { it.id == sessionId && it.state == SessionState.STOPPED }
            ?: error("Encerre o take antes de importar o vídeo.")
        val events = runtime.timelineFor(sessionId).getOrThrow()
        val review = runtime.processPostTake { postTake.importRecording(stopped, uri, events) }.getOrThrow()
        reviews[sessionId] = review
        val message = if (review.transcript.isNotEmpty()) {
            "Vídeo e marcas prontos. ${review.suggestedCuts.size} corte(s) sugerido(s) pela transcrição."
        } else {
            "Vídeo e marcas prontos. Transcrição local indisponível para este take."
        }
        _uiState.update { it.copy(exportState = ExportState(message = message)) }
        refreshTakes()
    }

    fun exportTake(sessionId: SessionId) = share(sessionId, marksOnly = false)
    fun exportMarks(sessionId: SessionId) = share(sessionId, marksOnly = true)
    private fun share(sessionId: SessionId, marksOnly: Boolean) = launchOperation {
        val review = take(sessionId)?.review ?: error("Take indisponível.")
        _uiState.update { it.copy(exportState = ExportState(ExportStatus.SHARING)) }
        val result = postTake.share(review, marksOnly)
        _uiState.update { it.copy(exportState = result) }
    }

    fun discardMedia(sessionId: SessionId) = launchOperation {
        reviews.remove(sessionId)?.let { postTake.discard(it) }
        _uiState.update { it.copy(exportState = ExportState(message = "Cópias locais temporárias descartadas.")) }
        refreshTakes()
    }

    private fun launchOperation(work: suspend () -> Unit) {
        viewModelScope.launch {
            preferencesReady.await()
            commands.withLock {
                _uiState.update { it.copy(working = true, commandError = null) }
                try { work() }
                catch (error: CancellationException) { throw error }
                catch (_: Exception) {
                    _uiState.update { it.copy(commandError = "Não foi possível concluir a operação. Confira o estado da sessão e tente novamente.",
                        exportState = it.exportState.takeUnless { value -> value.status == ExportStatus.SHARING } ?: ExportState(ExportStatus.ERROR)) }
                } finally { _uiState.update { it.copy(working = false) } }
            }
        }
    }

    private suspend fun refreshTakes() = refresh.withLock {
        val history = runtime.sessionHistory.value
        val active = runtime.snapshot.value
        val ids = (history.map { SessionId(it.sessionId) } + listOfNotNull(active?.id)).distinct()
        val takes = ids.mapNotNull { id ->
            val events = runtime.timelineFor(id).getOrNull() ?: return@mapNotNull null
            if (events.isEmpty()) return@mapNotNull null
            takeFromEvents(id, if (id == active?.id && active?.state in busyStates) "Take ao vivo" else "Take ${id.value.take(8)}", events, reviews[id])
        }.sortedByDescending { it.recordedAtEpochMs }
        _uiState.update { it.copy(takes = (seededTakes + takes).distinctBy { take -> take.sessionId }) }
    }

    private fun observeVoiceCommandSource(source: VoiceCommandSource) {
        viewModelScope.launch {
            source.state.collect { value ->
                if (activeVoiceCommandSource === source) {
                    _uiState.update { it.copy(voiceState = value) }
                }
            }
        }
        viewModelScope.launch {
            source.commands.collect {
                if (activeVoiceCommandSource === source) {
                    dispatch(LocalSessionCommand.valueOf(it.name), fromVoice = true)
                }
            }
        }
        viewModelScope.launch {
            source.errors.collect { error ->
                if (activeVoiceCommandSource === source) {
                    runtime.signal(
                        if (error == VoiceCommandError.INPUT_UNAVAILABLE) {
                            SessionSignal.VOICE_INPUT_UNAVAILABLE
                        } else {
                            SessionSignal.VOICE_COMMAND_ERROR
                        },
                    )
                }
            }
        }
    }

    override fun onCleared() {
        activeVoiceCommandSource?.close()
        runtime.close()
        super.onCleared()
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val preferences = OliviaPreferences(application)
            return OliviaSessionViewModel(
                runtime = SessionRuntimeFactory.create(application, ActiveBackend.current),
                backend = ActiveBackend.current,
                audioModes = preferences.audioMode,
                saveAudioMode = preferences::setAudioMode,
                postTake = AndroidPostTakeController(application, ActiveBackend.createRecordingSource(application)),
                voiceCommandSource = AndroidVoiceCommandSourceFactory.create(application),
                voiceCommandSourceFactory = { AndroidVoiceCommandSourceFactory.create(application) },
                initialTakes = seedTakes().takeIf { ActiveBackend.current.id == "mock" }.orEmpty(),
                recordingSource = ActiveBackend.createRecordingSource(application),
            ) as T
        }
    }
}
