package com.olivia.companion.session

import androidx.lifecycle.ViewModelStore
import com.olivia.companion.GlassesBackend
import com.olivia.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OliviaSessionViewModelTest {
    @Test fun viewModelUsesBackendLifecycleAndWaitsForStoppedBeforeDemoProcessing() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime = UiTestRuntime()
        val post = TestPostTake(runtime, demo = true)
        val model = OliviaSessionViewModel(runtime, GlassesBackend("mock", "Mock", false, false),
            flowOf(AudioMode.SIGNAL), {}, post)
        val store = ViewModelStore().apply { put("session", model) }
        try {
            runCurrent()
            assertEquals(AudioMode.SIGNAL, runtime.selectedMode)
            model.start(); runCurrent()
            assertEquals(SessionState.STARTED, model.uiState.value.session?.state)
            model.pause(); runCurrent()
            advanceTimeBy(5_000); runCurrent()
            assertEquals(SessionState.PAUSED, model.uiState.value.session?.state)
            model.resume(); runCurrent()
            model.stop(); runCurrent()
            assertEquals(SessionState.READY, model.uiState.value.session?.state)
            assertEquals(listOf(LocalSessionCommand.START, LocalSessionCommand.PAUSE,
                LocalSessionCommand.RESUME, LocalSessionCommand.STOP), runtime.commands)
            assertEquals(1, post.prepared)
            assertTrue(model.uiState.value.takes.single().events.any { it.type == TimelineEventType.SESSION_STOPPED })
            model.start(); runCurrent()
            assertEquals(SessionState.STARTED, model.uiState.value.session?.state)
        } finally { store.clear(); Dispatchers.resetMain() }
        assertTrue(runtime.closed)
    }

    @Test fun datWaitsForExplicitImportAndUiShowsBackendDegradationCauses() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime = UiTestRuntime()
        val post = TestPostTake(runtime, demo = false)
        val model = OliviaSessionViewModel(runtime, GlassesBackend("glasses", "DAT", false, true),
            flowOf(AudioMode.VOICE), {}, post)
        val store = ViewModelStore().apply { put("session", model) }
        try {
            model.start(); runCurrent()
            runtime.rig.coordinator.onAnalysisUnavailable(); runCurrent()
            assertEquals(GlassesConnection.CONNECTED, model.uiState.value.connection)
            assertEquals(setOf(DegradationCause.ANALYSIS_UNAVAILABLE), model.uiState.value.session?.degradationCauses)
            model.importTake(runtime.rig.id, "content://test/video"); runCurrent()
            assertEquals(0, post.imported)
            model.stop(); runCurrent()
            assertEquals(SessionState.STOPPED, model.uiState.value.session?.state)
            assertEquals(0, post.prepared)
            model.importTake(runtime.rig.id, "content://test/video"); runCurrent()
            assertEquals(1, post.imported)
            assertEquals(SessionState.READY, model.uiState.value.session?.state)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}

private class UiTestRuntime : SessionRuntime {
    val rig = SessionRig()
    var selectedMode = AudioMode.VOICE
    var closed = false
    val commands = mutableListOf<LocalSessionCommand>()
    override val snapshot = rig.coordinator.snapshot
    override val connection = MutableStateFlow(GlassesConnection.CONNECTED)
    override val audioStatus = MutableStateFlow(AudioFeedbackStatus.READY)
    override val audioStatusMessage = MutableStateFlow<String?>(null)
    override val liveLoopMetrics = MutableStateFlow(LiveLoopMetrics().snapshot())
    override val liveGuidanceStatus = MutableStateFlow(LiveGuidanceStatus.IDLE)
    override val livePreview = MutableStateFlow<LivePreviewFrame?>(null)
    override val liveGuidanceDirection = MutableStateFlow<Direction?>(null)
    override val sessionHistory = MutableStateFlow(emptyList<SessionHistoryEntry>())
    override suspend fun setAudioMode(mode: AudioMode): Result<Unit> { selectedMode = mode; return Result.success(Unit) }
    override suspend fun signal(signal: SessionSignal) = rig.audio.signal(signal)
    override suspend fun timelineFor(sessionId: SessionId) = Result.success(rig.timeline.events(sessionId))
    override suspend fun handle(command: LocalSessionCommand): Result<SessionSnapshot> {
        commands += command
        when (command) {
            LocalSessionCommand.START -> rig.start()
            LocalSessionCommand.PAUSE -> rig.coordinator.pause().getOrThrow()
            LocalSessionCommand.RESUME -> { rig.coordinator.resume().getOrThrow(); rig.heartbeat() }
            LocalSessionCommand.STOP -> rig.coordinator.stop().getOrThrow()
        }
        return Result.success(requireNotNull(snapshot.value))
    }
    override suspend fun processPostTake(work: suspend (SessionId) -> Result<SessionReview>): Result<SessionReview> {
        val id = rig.coordinator.beginPostProcessing().getOrThrow().id
        return work(id).also { rig.coordinator.completePostProcessing(id) }
    }
    override fun close() { closed = true }
}

private class TestPostTake(private val runtime: UiTestRuntime, demo: Boolean) : PostTakeController {
    override val hasDemoRecording = demo
    var prepared = 0
    var imported = 0
    override suspend fun prepareDemo(session: SessionSnapshot, events: List<TimelineEvent>): Result<SessionReview> {
        assertEquals(SessionState.PROCESSING, runtime.snapshot.value?.state)
        assertTrue(events.any { it.type == TimelineEventType.SESSION_STOPPED })
        prepared++
        return Result.success(SessionReview(session.id, null, emptyList(), emptyList(), events))
    }
    override suspend fun importRecording(session: SessionSnapshot, uri: String, events: List<TimelineEvent>): Result<SessionReview> {
        assertEquals(SessionState.PROCESSING, runtime.snapshot.value?.state)
        imported++
        return Result.success(SessionReview(session.id, null, emptyList(), emptyList(), events))
    }
    override suspend fun share(review: SessionReview, marksOnly: Boolean) = ExportState(ExportStatus.SHARED)
    override suspend fun discard(review: SessionReview) = Unit
}
