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
    clock: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {
    private val timeline = InMemoryTimeline()
    private val runtime = DebugSessionRuntime(timeline, clock)
    private var ticker: Job? = null

    private val _uiState = MutableStateFlow(
        OliviaUiState(
            backend = backend,
            audioMode = audioModeStore.current(),
            session = null,
            connection = initialConnection(backend, null),
            elapsedMs = 0,
            takes = listOf(demoTake()),
        ),
    )
    val uiState: StateFlow<OliviaUiState> = _uiState.asStateFlow()

    init {
        timeline.replace(MockDemoTake.sessionId, MockDemoTake.events())
        publish()
    }

    fun setAudioMode(mode: AudioMode) {
        if (_uiState.value.isLive) return
        audioModeStore.set(mode)
        publish()
    }

    fun start() {
        runtime.start(SessionConfig(audioMode = _uiState.value.audioMode))
        startTicker()
        publish()
    }

    fun pause() {
        runtime.pause()
        publish()
    }

    fun resume() {
        runtime.resume()
        startTicker()
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
        val demo = demoTake()
        val live = runtime.snapshot?.id
            ?.takeIf { it != MockDemoTake.sessionId }
            ?.let { id ->
                takeFromEvents(
                    sessionId = id,
                    title = "Take ao vivo",
                    events = timeline.events(id),
                    isDemo = false,
                )
            }
        return listOfNotNull(live, demo)
    }

    private fun demoTake(): TakeSummary = takeFromEvents(
        sessionId = MockDemoTake.sessionId,
        title = "Ensaio mock",
        events = MockDemoTake.events(),
        isDemo = true,
        review = MockDemoTake.review(),
    )

    private fun initialConnection(
        backend: GlassesBackend,
        session: SessionSnapshot?,
    ): GlassesConnection = when {
        session?.state == SessionState.DEGRADED -> GlassesConnection.DISCONNECTED
        backend.requiresPhysicalGlasses -> GlassesConnection.UNAVAILABLE
        else -> GlassesConnection.CONNECTED
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
