package com.olivia.companion.session

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.olivia.companion.ActiveBackend
import com.olivia.companion.GlassesBackend
import com.olivia.core.AudioMode
import com.olivia.core.GlassesConnection
import com.olivia.core.SessionConfig
import com.olivia.core.SessionId
import com.olivia.core.SessionSnapshot
import com.olivia.core.SessionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OliviaUiState(
    val backend: GlassesBackend,
    val audioMode: AudioMode,
    val session: SessionSnapshot?,
    val connection: GlassesConnection,
    val elapsedMs: Long,
    val takes: List<TakeSummary>,
) {
    val isLive: Boolean
        get() = session?.state in setOf(
            SessionState.STARTED,
            SessionState.PAUSED,
            SessionState.DEGRADED,
        )
}

class OliviaSessionViewModel(
    private val audioModeStore: AudioModeStore,
    backend: GlassesBackend,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {
    private val timeline = InMemoryTimeline()
    private val runtime = DebugSessionRuntime(timeline, clock)
    private val mockGateway = if (backend.requiresPhysicalGlasses) {
        null
    } else {
        MockFrameGateway(clock = clock)
    }
    private val mockAnalyzer = if (backend.requiresPhysicalGlasses) {
        null
    } else {
        MockFrameAnalyzer(clock = clock)
    }
    private var frameJob: Job? = null
    private var gatewayCommand: Job? = null
    private var ticker: Job? = null
    private var watchdog: Job? = null

    private val _uiState = MutableStateFlow(
        OliviaUiState(
            backend = backend,
            audioMode = audioModeStore.current(),
            session = null,
            connection = initialConnection(backend, null),
            elapsedMs = 0,
            takes = emptyList(),
        ),
    )
    val uiState: StateFlow<OliviaUiState> = _uiState.asStateFlow()

    init {
        frameJob = mockGateway?.let { gateway ->
            viewModelScope.launch {
                gateway.frames.collect { frame ->
                    mockAnalyzer?.analyze(frame)?.onFailure { }
                    runtime.frameReceived(frame.capturedAtEpochMs)
                    publish()
                }
            }
        }
        publish()
    }

    fun setAudioMode(mode: AudioMode) {
        if (_uiState.value.isLive) return
        audioModeStore.set(mode)
        publish()
    }

    fun start() {
        val config = SessionConfig(audioMode = _uiState.value.audioMode)
        val started = runtime.start(config)
        if (started != null) {
            startGateway(started.id, config)
        }
        startTicker()
        startWatchdog()
        publish()
    }

    fun pause() {
        runtime.pause()
        gatewayCommand = mockGateway?.let { gateway ->
            viewModelScope.launch { gateway.pauseCapture() }
        }
        publish()
    }

    fun resume() {
        runtime.resume()
        gatewayCommand = mockGateway?.let { gateway ->
            viewModelScope.launch { gateway.resumeCapture() }
        }
        startTicker()
        startWatchdog()
        publish()
    }

    fun degrade() {
        runtime.degrade()
        publish()
    }

    fun recover() {
        runtime.recover()
        startTicker()
        publish()
    }

    fun stop() {
        runtime.stop()
        ticker?.cancel()
        ticker = null
        watchdog?.cancel()
        watchdog = null
        gatewayCommand = mockGateway?.let { gateway ->
            viewModelScope.launch { gateway.stopCapture() }
        }
        publish()
    }

    fun take(sessionId: SessionId): TakeSummary? =
        _uiState.value.takes.firstOrNull { it.sessionId == sessionId }

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
        gatewayCommand = mockGateway?.let { gateway ->
            viewModelScope.launch { gateway.startCapture(sessionId, config) }
        }
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
                val lastFrameAt = session.lastFrameAtEpochMs ?: continue
                if (
                    session.state == SessionState.STARTED &&
                    clock() - lastFrameAt >= session.config.degradedAfterMs
                ) {
                    runtime.degrade()
                    publish()
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
                connection = initialConnection(it.backend, session),
                elapsedMs = session?.elapsedMs ?: 0,
                takes = buildTakes(),
            )
        }
    }

    private fun buildTakes(): List<TakeSummary> {
        val live = runtime.snapshot?.id
            ?.let { id ->
                takeFromEvents(
                    sessionId = id,
                    title = "Take ao vivo",
                    events = timeline.events(id),
                    isDemo = false,
                )
            }
        return listOfNotNull(live)
    }

    private fun initialConnection(
        backend: GlassesBackend,
        session: SessionSnapshot?,
    ): GlassesConnection = when {
        session?.state == SessionState.DEGRADED -> GlassesConnection.DISCONNECTED
        backend.requiresPhysicalGlasses -> GlassesConnection.UNAVAILABLE
        else -> GlassesConnection.CONNECTED
    }

    override fun onCleared() {
        mockGateway?.close()
        frameJob?.cancel()
        gatewayCommand?.cancel()
        watchdog?.cancel()
        ticker?.cancel()
        super.onCleared()
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return OliviaSessionViewModel(
                audioModeStore = AudioModeStore(application),
                backend = ActiveBackend.current,
            ) as T
        }
    }
}
