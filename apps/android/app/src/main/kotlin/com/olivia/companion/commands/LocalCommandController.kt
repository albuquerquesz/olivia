package com.olivia.companion.commands

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.olivia.companion.session.LocalSessionCommand
import com.olivia.companion.session.SessionRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

/**
 * Boundary for the phone-only command recognizer. It exposes a parsed command, never raw audio
 * or a transcript, so neither can accidentally reach the visual-analysis or timeline paths.
 */
interface LocalCommandRecognizer : AutoCloseable {
    /** False means the app must not ask for microphone permission. */
    val isAvailable: Boolean
    val commands: Flow<LocalSessionCommand>
    suspend fun start()
    suspend fun stop()
}

/**
 * The production implementation is deliberately unavailable until a Porcupine access key and
 * the approved pt-BR models are supplied outside version control. It makes no microphone or
 * network request while unavailable.
 */
class UnavailablePorcupineRecognizer : LocalCommandRecognizer {
    override val isAvailable: Boolean = false
    override val commands: Flow<LocalSessionCommand> = emptyFlow()
    override suspend fun start() = Unit
    override suspend fun stop() = Unit
    override fun close() = Unit
}

/**
 * Owns microphone permission and forwards only recognized local commands to the session.
 * Calling [start] repeatedly is safe; the recognizer itself owns its capture lifecycle.
 */
class LocalCommandController(
    private val activity: ComponentActivity,
    private val runtime: SessionRuntime,
    private val recognizer: LocalCommandRecognizer,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var commandCollection: Job? = null
    private var started = false
    private val permissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && started) collectCommands()
    }

    fun start() {
        started = true
        if (!recognizer.isAvailable) return
        if (hasMicrophonePermission()) collectCommands() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun stop() {
        started = false
        commandCollection?.cancel()
        commandCollection = null
        scope.launch { recognizer.stop() }
    }

    override fun close() {
        stop()
        recognizer.close()
        scope.cancel()
    }

    private fun collectCommands() {
        if (commandCollection != null) return
        commandCollection = scope.launch {
            recognizer.start()
            recognizer.commands.collect { command ->
                // Failures are already represented by SessionRuntime; do not store speech data.
                runtime.handle(command)
            }
        }
    }

    private fun hasMicrophonePermission(): Boolean =
        ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
}
