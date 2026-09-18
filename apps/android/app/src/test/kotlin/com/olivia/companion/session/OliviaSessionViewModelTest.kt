package com.olivia.companion.session

import com.olivia.companion.GlassesBackend
import com.olivia.core.AudioMode
import com.olivia.core.GlassesConnection
import com.olivia.core.SessionState
import com.olivia.core.SessionSignal
import com.olivia.core.TimelineEventType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OliviaSessionViewModelTest {
    @Test
    fun mockOrchestratesPauseResumeAndStopWithoutWatchdogFalsePositive() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val clock = { START_TIME_MS + testScheduler.currentTime }
        val gateway = ScenarioGlassesGateway(
            clock = clock,
            dispatcher = dispatcher,
            fixtures = listOf(
                scenarioFrame(0L, SessionScenario.NORMAL_MARKER),
                scenarioFrame(2_000L, SessionScenario.NORMAL_MARKER),
                scenarioFrame(4_000L, SessionScenario.NORMAL_MARKER),
            ),
        )
        val audio = RecordingAudioFeedback()
        val viewModel = newViewModel(gateway, audio, clock)

        try {
            viewModel.start()
            runCurrent()
            assertEquals(SessionState.STARTED, viewModel.uiState.value.session?.state)

            advanceTimeBy(1_000L)
            runCurrent()
            viewModel.pause()
            runCurrent()
            assertEquals(SessionState.PAUSED, viewModel.uiState.value.session?.state)

            advanceTimeBy(3_000L)
            runCurrent()
            assertEquals(SessionState.PAUSED, viewModel.uiState.value.session?.state)

            viewModel.resume()
            runCurrent()
            assertEquals(SessionState.STARTED, viewModel.uiState.value.session?.state)

            // O frame originalmente previsto para 00:02 chega um segundo depois da retomada.
            advanceTimeBy(1_000L)
            runCurrent()
            assertEquals(SessionState.STARTED, viewModel.uiState.value.session?.state)

            viewModel.stop()
            runCurrent()
            advanceUntilIdle()

            assertEquals(SessionState.STOPPED, viewModel.uiState.value.session?.state)
            assertEquals(
                listOf(
                    TimelineEventType.SESSION_STARTED,
                    TimelineEventType.SESSION_PAUSED,
                    TimelineEventType.SESSION_RESUMED,
                    TimelineEventType.SESSION_STOPPED,
                ),
                viewModel.uiState.value.takes.single().events
                    .filter { it.type.name.startsWith("SESSION_") }
                    .map { it.type },
            )
            assertEquals(
                listOf(
                    SessionSignal.SESSION_STARTED,
                    SessionSignal.SESSION_PAUSED,
                    SessionSignal.SESSION_RESUMED,
                    SessionSignal.SESSION_STOPPED,
                ),
                audio.signals,
            )
            assertFalse(audio.signals.contains(SessionSignal.CONNECTION_LOST))
        } finally {
            gateway.close()
            audio.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun mockDegradesAfterFourSecondsRecoversAndPreservesEarlierMarks() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val clock = { START_TIME_MS + testScheduler.currentTime }
        val gateway = ScenarioGlassesGateway(
            clock = clock,
            dispatcher = dispatcher,
            fixtures = listOf(
                scenarioFrame(0L, SessionScenario.NORMAL_MARKER),
                scenarioFrame(2_000L, SessionScenario.FRAMING_MARKER),
                scenarioFrame(4_000L, SessionScenario.NORMAL_MARKER),
                scenarioFrame(10_000L, SessionScenario.NORMAL_MARKER),
            ),
        )
        val audio = RecordingAudioFeedback()
        val viewModel = newViewModel(gateway, audio, clock)
        val frameOffsets = mutableListOf<Long>()
        val frameJob = backgroundScope.launch(dispatcher) {
            gateway.frames.collect { frame ->
                frameOffsets += frame.capturedAtEpochMs - START_TIME_MS
            }
        }

        try {
            viewModel.start()
            runCurrent()
            advanceTimeBy(4_000L)
            runCurrent()
            assertEquals(SessionState.STARTED, viewModel.uiState.value.session?.state)

            // A janela de polling é menor que o timeout, mas não pode antecipá-lo.
            advanceTimeBy(3_999L)
            runCurrent()
            assertEquals(SessionState.STARTED, viewModel.uiState.value.session?.state)

            advanceTimeBy(1L)
            runCurrent()
            assertEquals(SessionState.DEGRADED, viewModel.uiState.value.session?.state)
            assertEquals(GlassesConnection.DISCONNECTED, viewModel.uiState.value.connection)

            advanceTimeBy(2_000L)
            runCurrent()
            assertEquals(SessionState.STARTED, viewModel.uiState.value.session?.state)
            assertEquals(GlassesConnection.CONNECTED, viewModel.uiState.value.connection)
            assertEquals(listOf(0L, 2_000L, 4_000L, 10_000L), frameOffsets)

            viewModel.stop()
            runCurrent()
            advanceUntilIdle()

            val events = viewModel.uiState.value.takes.single().events
            val types = events.map { it.type }
            assertTrue(
                events.any {
                    it.type == TimelineEventType.DIAGNOSIS && it.offsetMs == 2_000L
                },
            )
            assertTrue(
                events.any {
                    it.type == TimelineEventType.CORRECTION_ISSUED && it.offsetMs == 2_000L
                },
            )
            assertTrue(types.contains(TimelineEventType.CORRECTION_ISSUED))
            assertTrue(types.contains(TimelineEventType.CORRECTION_RESOLVED))
            assertTrue(types.contains(TimelineEventType.SESSION_DEGRADED))
            assertTrue(types.contains(TimelineEventType.SESSION_RECOVERED))
            assertTrue(types.contains(TimelineEventType.SESSION_STOPPED))
            assertEquals(
                listOf(
                    SessionSignal.SESSION_STARTED,
                    SessionSignal.CONNECTION_LOST,
                    SessionSignal.CONNECTION_RESTORED,
                    SessionSignal.SESSION_STOPPED,
                ),
                audio.signals,
            )
            assertFalse(audio.signals.contains(SessionSignal.SESSION_PAUSED))
        } finally {
            frameJob.cancel()
            gateway.close()
            audio.close()
            Dispatchers.resetMain()
        }
    }

    private fun newViewModel(
        gateway: ScenarioGlassesGateway,
        audio: RecordingAudioFeedback,
        clock: () -> Long,
    ): OliviaSessionViewModel = OliviaSessionViewModel(
        audioModeStore = InMemoryAudioModeRepository(),
        backend = GlassesBackend(
            id = "mock",
            label = "Óculos simulados",
            requiresPhysicalGlasses = false,
            usesDatSdk = false,
        ),
        glassesGateway = gateway,
        frameAnalyzer = ScenarioFrameAnalyzer(clock),
        postProcessor = TimelinePostProcessor(),
        audioFeedback = audio,
        clock = clock,
    )

    private fun scenarioFrame(offsetMs: Long, marker: Int): ScenarioFrame = ScenarioFrame(
        offsetMs = offsetMs,
        marker = marker,
        expectedKind = null,
    )

    private class InMemoryAudioModeRepository : AudioModeRepository {
        private var mode = AudioMode.VOICE

        override fun current(): AudioMode = mode

        override fun set(mode: AudioMode) {
            this.mode = mode
        }
    }

    private class RecordingAudioFeedback : AudioFeedbackController {
        private val _status = MutableStateFlow(AudioFeedbackStatus.READY)
        override val status: StateFlow<AudioFeedbackStatus> = _status.asStateFlow()
        override val statusMessage: StateFlow<String?> = MutableStateFlow(null).asStateFlow()
        override val mode: AudioMode = AudioMode.VOICE
        val signals = mutableListOf<SessionSignal>()

        override suspend fun announce(correction: com.olivia.core.Correction) = Unit

        override suspend fun signal(signal: SessionSignal) {
            signals += signal
        }

        override suspend fun stop() = Unit

        override fun close() = Unit
    }

    private companion object {
        const val START_TIME_MS = 1_000_000L
    }
}
