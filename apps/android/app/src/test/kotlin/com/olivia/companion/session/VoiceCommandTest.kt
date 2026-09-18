package com.olivia.companion.session

import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceCommandTest {
    @Test
    fun parsesOnlyTheFourSupportedCommands() {
        assertEquals(VoiceCommand.START, parseVoiceCommand("gravar"))
        assertEquals(VoiceCommand.PAUSE, parseVoiceCommand("Oi, Olivia, pausar"))
        assertEquals(VoiceCommand.RESUME, parseVoiceCommand("retomar"))
        assertEquals(VoiceCommand.STOP, parseVoiceCommand("terminar"))
    }

    @Test
    fun rejectsCommandsOutsideTheClosedVocabulary() {
        assertNull(parseVoiceCommand("abrir configurações"))
        assertNull(parseVoiceCommand("não gravar"))
        assertNull(parseVoiceCommand("Oi, Olivia"))
    }

    @Test
    fun localSourceRestartsWakeWordAfterARecognizedCommand() = runTest {
        val wakeWord = FakeWakeWordDetector()
        val recognizer = FakeLocalCommandRecognizer()
        val source = LocalVoiceCommandSource(
            wakeWordDetector = wakeWord,
            commandRecognizer = recognizer,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        val commands = mutableListOf<VoiceCommand>()
        val errors = mutableListOf<VoiceCommandError>()
        val commandJob = launch { source.commands.collect { commands += it } }
        val errorJob = launch { source.errors.collect { errors += it } }

        source.start()
        advanceUntilIdle()
        assertEquals(VoiceCommandState.LISTENING_FOR_WAKE_WORD, source.state.value)

        source.onWakeWordDetected()
        advanceUntilIdle()
        assertEquals(VoiceCommandState.LISTENING_FOR_COMMAND, source.state.value)
        assertEquals(1, recognizer.starts)

        source.onCommandCandidates(listOf("pausar"))
        advanceUntilIdle()
        assertEquals(listOf(VoiceCommand.PAUSE), commands)
        assertEquals(VoiceCommandState.LISTENING_FOR_WAKE_WORD, source.state.value)
        assertEquals(2, wakeWord.starts)

        source.onWakeWordDetected()
        advanceUntilIdle()
        source.onCommandCandidates(listOf("abrir ajustes"))
        advanceUntilIdle()
        assertEquals(listOf(VoiceCommandError.COMMAND_NOT_RECOGNIZED), errors)

        commandJob.cancel()
        errorJob.cancel()
        source.close()
    }

    private class FakeWakeWordDetector : WakeWordDetector {
        var starts = 0

        override fun start() {
            starts += 1
        }

        override fun stop() = Unit

        override fun close() = Unit
    }

    private class FakeLocalCommandRecognizer : LocalCommandRecognizer {
        var starts = 0

        override fun start() {
            starts += 1
        }

        override fun stop() = Unit

        override fun close() = Unit
    }
}
