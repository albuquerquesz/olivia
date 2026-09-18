package com.olivia.companion.session

import com.olivia.companion.GlassesBackend
import androidx.lifecycle.ViewModelStore
import com.olivia.core.AudioMode
import com.olivia.core.ClockReading
import com.olivia.core.Confidence
import com.olivia.core.CorrectionStatus
import com.olivia.core.DegradationCause
import com.olivia.core.Direction
import com.olivia.core.GlassesConnection
import com.olivia.core.LiveLoopMetrics
import com.olivia.core.SessionConfig
import com.olivia.core.SessionId
import com.olivia.core.SessionReview
import com.olivia.core.SessionSignal
import com.olivia.core.SessionSnapshot
import com.olivia.core.SessionState
import com.olivia.core.TimelineEvent
import com.olivia.core.TimelineEventType
import com.olivia.core.VisualDiagnosis
import com.olivia.core.DiagnosisKind
import com.olivia.core.Severity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Offline product gate: five repeated takes through the local voice boundary and UI runtime.
 * It intentionally does not claim physical microphone, Porcupine silicon or headset evidence.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineHandsFreeGateTest {
    @Test
    fun fiveTakesKeepVoiceLocalAndPreserveCorrectionsDegradationAndRecovery() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val voice = LocalVoiceCommandSource(
            wakeWordDetector = FakeWakeWordDetector(),
            commandRecognizer = FakeCommandRecognizer(),
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        val runtime = GateRuntime()
        val postTake = GatePostTake(runtime)
        val model = OliviaSessionViewModel(
            runtime = runtime,
            backend = GlassesBackend("mock", "Offline", false, false),
            audioModes = flowOf(AudioMode.VOICE),
            saveAudioMode = {},
            postTake = postTake,
            voiceCommandSource = voice,
        )
        val store = ViewModelStore().apply { put("offline-gate", model) }

        try {
            runCurrent()
            voice.start()
            runCurrent()
            repeat(5) {
                voice.onWakeWordDetected()
                runCurrent()
                voice.onCommandCandidates(listOf("gravar"))
                runCurrent()
                assertEquals(SessionState.STARTED, runtime.snapshot.value?.state)

                voice.onWakeWordDetected()
                runCurrent()
                voice.onCommandCandidates(listOf("pausar"))
                runCurrent()
                assertEquals(SessionState.PAUSED, runtime.snapshot.value?.state)

                voice.onWakeWordDetected()
                runCurrent()
                voice.onCommandCandidates(listOf("retomar"))
                runCurrent()
                assertEquals(SessionState.STARTED, runtime.snapshot.value?.state)

                voice.onWakeWordDetected()
                runCurrent()
                voice.onCommandCandidates(listOf("terminar"))
                runCurrent()
                assertEquals(SessionState.READY, runtime.snapshot.value?.state)
            }

            assertEquals(5, postTake.prepared)
            assertEquals(5, runtime.timelines.size)
            assertEquals(20, runtime.commands.size)
            runtime.timelines.values.forEach { events ->
                assertEquals(2, events.count { it.type == TimelineEventType.CORRECTION_RESOLVED })
                assertTrue(events.any { it.type == TimelineEventType.SESSION_DEGRADED })
                assertTrue(events.any { it.type == TimelineEventType.SESSION_RECOVERED })
                assertTrue(events.any { it.type == TimelineEventType.SESSION_STOPPED })
            }
        } finally {
            voice.close()
            store.clear()
            Dispatchers.resetMain()
        }
    }
}

private class FakeWakeWordDetector : WakeWordDetector {
    override fun start() = Unit
    override fun stop() = Unit
    override fun close() = Unit
}

private class FakeCommandRecognizer : LocalCommandRecognizer {
    override fun start() = Unit
    override fun stop() = Unit
    override fun close() = Unit
}

private class GateRuntime : SessionRuntime {
    private val _snapshot = MutableStateFlow<SessionSnapshot?>(null)
    override val snapshot: StateFlow<SessionSnapshot?> = _snapshot.asStateFlow()
    override val connection = MutableStateFlow(GlassesConnection.CONNECTED)
    override val audioStatus = MutableStateFlow(AudioFeedbackStatus.READY)
    override val audioStatusMessage = MutableStateFlow<String?>(null)
    override val liveLoopMetrics = MutableStateFlow(LiveLoopMetrics().snapshot())
    override val liveGuidanceStatus = MutableStateFlow(LiveGuidanceStatus.IDLE)
    override val livePreview = MutableStateFlow<LivePreviewFrame?>(null)
    override val liveGuidanceDirection = MutableStateFlow<Direction?>(null)
    override val sessionHistory = MutableStateFlow<List<SessionHistoryEntry>>(emptyList())
    val commands = mutableListOf<LocalSessionCommand>()
    val timelines = linkedMapOf<SessionId, List<TimelineEvent>>()
    private var takeNumber = 0
    private var events = mutableListOf<TimelineEvent>()

    override suspend fun handle(command: LocalSessionCommand): Result<SessionSnapshot> {
        commands += command
        val current = _snapshot.value
        val id = current?.id ?: SessionId("take-${takeNumber + 1}")
        when (command) {
            LocalSessionCommand.START -> {
                takeNumber += 1
                val freshId = SessionId("take-$takeNumber")
                events = mutableListOf(event(freshId, TimelineEventType.SESSION_STARTED, 0))
                events += correctionEvents(freshId)
                _snapshot.value = snapshot(freshId, SessionState.STARTED)
            }
            LocalSessionCommand.PAUSE -> {
                events += event(id, TimelineEventType.SESSION_PAUSED, 2_000)
                _snapshot.value = snapshot(id, SessionState.PAUSED)
            }
            LocalSessionCommand.RESUME -> {
                events += event(id, TimelineEventType.SESSION_RESUMED, 4_000)
                events += event(id, TimelineEventType.SESSION_DEGRADED, 5_000)
                events += event(id, TimelineEventType.SESSION_RECOVERED, 9_000)
                _snapshot.value = snapshot(id, SessionState.STARTED)
            }
            LocalSessionCommand.STOP -> {
                events += event(id, TimelineEventType.SESSION_STOPPED, 12_000)
                timelines[id] = events.toList()
                _snapshot.value = snapshot(id, SessionState.STOPPED)
            }
        }
        return Result.success(requireNotNull(_snapshot.value))
    }

    override suspend fun setAudioMode(mode: AudioMode) = Result.success(Unit)
    override suspend fun signal(signal: SessionSignal) = Result.success(ClockReading(0, 0))
    override suspend fun timelineFor(sessionId: SessionId) = Result.success(timelines[sessionId].orEmpty())
    override suspend fun processPostTake(work: suspend (SessionId) -> Result<SessionReview>): Result<SessionReview> {
        val id = requireNotNull(_snapshot.value).id
        _snapshot.value = snapshot(id, SessionState.PROCESSING)
        val result = work(id)
        _snapshot.value = snapshot(id, if (result.isSuccess) SessionState.READY else SessionState.ERROR)
        return result
    }

    override fun close() = Unit

    private fun snapshot(id: SessionId, state: SessionState): SessionSnapshot = SessionSnapshot(
        id = id,
        state = state,
        startedAtEpochMs = 1_000,
        elapsedMs = 12_000,
        config = SessionConfig(),
        lastFrameAtEpochMs = 10_000,
        degradationCauses = if (state == SessionState.DEGRADED) setOf(DegradationCause.CAPTURE_UNAVAILABLE) else emptySet(),
    )
}

private class GatePostTake(private val runtime: GateRuntime) : PostTakeController {
    override val hasDemoRecording = true
    var prepared = 0

    override suspend fun prepareDemo(session: SessionSnapshot, events: List<TimelineEvent>): Result<SessionReview> {
        assertEquals(SessionState.PROCESSING, runtime.snapshot.value?.state)
        prepared += 1
        return Result.success(SessionReview(session.id, null, emptyList(), emptyList(), events))
    }

    override suspend fun importRecording(session: SessionSnapshot, uri: String, events: List<TimelineEvent>) =
        Result.failure<SessionReview>(UnsupportedOperationException())

    override suspend fun share(review: SessionReview, marksOnly: Boolean) = ExportState(ExportStatus.SHARED)
    override suspend fun discard(review: SessionReview) = Unit
}

private fun correctionEvents(id: SessionId): List<TimelineEvent> = listOf(
    event(id, TimelineEventType.CORRECTION_ISSUED, 1_000, "correction-1", CorrectionStatus.PENDING),
    event(id, TimelineEventType.CORRECTION_RESOLVED, 2_000, "correction-1", CorrectionStatus.OBEYED),
    event(id, TimelineEventType.CORRECTION_ISSUED, 3_000, "correction-2", CorrectionStatus.PENDING),
    event(id, TimelineEventType.CORRECTION_RESOLVED, 4_000, "correction-2", CorrectionStatus.OBEYED),
)

private fun event(
    sessionId: SessionId,
    type: TimelineEventType,
    offsetMs: Long,
    suffix: String = type.name.lowercase(),
    status: CorrectionStatus? = null,
): TimelineEvent = TimelineEvent(
    id = "${sessionId.value}-$suffix-$offsetMs",
    sessionId = sessionId,
    atEpochMs = 1_000 + offsetMs,
    offsetMs = offsetMs,
    type = type,
    diagnosis = if (status != null) VisualDiagnosis(
        DiagnosisKind.FRAMING,
        Severity.MAJOR,
        Confidence(0.9f),
        Direction.TURN_RIGHT,
    ) else null,
    direction = status?.let { Direction.TURN_RIGHT },
    confidence = status?.let { Confidence(0.9f) },
    correctionStatus = status,
    correctionId = suffix.takeIf { status != null },
    label = type.name,
)
