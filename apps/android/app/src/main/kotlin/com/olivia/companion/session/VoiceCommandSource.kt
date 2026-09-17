package com.olivia.companion.session


import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class VoiceCommandState {
    IDLE,
    LISTENING_FOR_WAKE_WORD,
    LISTENING_FOR_COMMAND,
    UNAVAILABLE,
    ERROR,
}

enum class VoiceCommandError {
    COMMAND_NOT_RECOGNIZED,
    INPUT_UNAVAILABLE,
    ENGINE_ERROR,
}

/** Fonte de comandos local; não expõe áudio bruto nem depende da sessão. */
interface VoiceCommandSource : AutoCloseable {
    val commands: Flow<VoiceCommand>
    val state: StateFlow<VoiceCommandState>
    val errors: Flow<VoiceCommandError>

    fun start()

    fun stop()
}

/** Porta mínima usada pelo orquestrador, permitindo doubles nos testes. */
internal interface WakeWordDetector : AutoCloseable {
    fun start()

    fun stop()
}

/** Reconhecedor de uma fala curta depois que o wake word foi detectado. */
internal interface LocalCommandRecognizer : AutoCloseable {
    fun start()

    fun stop()
}

/**
 * Coordena a janela wake word → comando sem acoplar a sessão ou a UI a um SDK.
 * O detector escuta apenas o wake word; o reconhecedor recebe uma única fala curta.
 */
internal class LocalVoiceCommandSource(
    private val wakeWordDetector: WakeWordDetector,
    private val commandRecognizer: LocalCommandRecognizer,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : VoiceCommandSource {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _commands = MutableSharedFlow<VoiceCommand>(extraBufferCapacity = 8)
    private val _state = MutableStateFlow(VoiceCommandState.IDLE)
    private val _errors = MutableSharedFlow<VoiceCommandError>(extraBufferCapacity = 8)
    private var active = false
    private var closed = false

    override val commands: Flow<VoiceCommand> = _commands.asSharedFlow()
    override val state: StateFlow<VoiceCommandState> = _state.asStateFlow()
    override val errors: Flow<VoiceCommandError> = _errors.asSharedFlow()

    override fun start() {
        scope.launch {
            if (closed || active) return@launch
            active = true
            startWakeWord()
        }
    }

    override fun stop() {
        scope.launch {
            if (closed) return@launch
            active = false
            runCatching { commandRecognizer.stop() }
            runCatching { wakeWordDetector.stop() }
            _state.value = VoiceCommandState.IDLE
        }
    }

    /** Chamado pelo adapter quando o Porcupine detecta o wake word. */
    internal fun onWakeWordDetected() {
        scope.launch {
            if (closed || !active || _state.value != VoiceCommandState.LISTENING_FOR_WAKE_WORD) {
                return@launch
            }
            runCatching {
                wakeWordDetector.stop()
                commandRecognizer.start()
            }.onSuccess {
                _state.value = VoiceCommandState.LISTENING_FOR_COMMAND
            }.onFailure {
                handleEngineError()
            }
        }
    }

    /** Recebe somente candidatos textuais transitórios do reconhecedor local. */
    internal fun onCommandCandidates(candidates: List<String>) {
        scope.launch {
            if (closed || !active || _state.value != VoiceCommandState.LISTENING_FOR_COMMAND) {
                return@launch
            }
            runCatching { commandRecognizer.stop() }
            val recognized = candidates.asSequence()
                .mapNotNull(::parseVoiceCommand)
                .firstOrNull()
            if (recognized != null) {
                _commands.tryEmit(recognized)
            } else {
                _errors.tryEmit(VoiceCommandError.COMMAND_NOT_RECOGNIZED)
            }
            restartWakeWord()
        }
    }

    /** Erros de uma janela de reconhecimento não encerram o listener local. */
    internal fun onCommandError() {
        scope.launch {
            if (closed || !active || _state.value != VoiceCommandState.LISTENING_FOR_COMMAND) {
                return@launch
            }
            runCatching { commandRecognizer.stop() }
            _errors.tryEmit(VoiceCommandError.COMMAND_NOT_RECOGNIZED)
            restartWakeWord()
        }
    }

    /** Erro fatal do detector: não tenta trocar para reconhecimento online. */
    internal fun onWakeWordError() {
        scope.launch {
            if (closed) return@launch
            handleEngineError()
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        active = false
        runCatching { commandRecognizer.close() }
        runCatching { wakeWordDetector.close() }
        scope.cancel()
    }

    private fun startWakeWord() {
        runCatching { wakeWordDetector.start() }
            .onSuccess {
                _state.value = VoiceCommandState.LISTENING_FOR_WAKE_WORD
            }
            .onFailure { handleEngineError() }
    }

    private fun restartWakeWord() {
        if (!active || closed) return
        startWakeWord()
    }

    private fun handleEngineError() {
        active = false
        runCatching { commandRecognizer.stop() }
        runCatching { wakeWordDetector.stop() }
        _state.value = VoiceCommandState.ERROR
        _errors.tryEmit(VoiceCommandError.ENGINE_ERROR)
    }
}

/** Fonte usada quando o runtime não tem credencial/modelo/serviço local configurado. */
internal class UnavailableVoiceCommandSource : VoiceCommandSource {
    private val _commands = MutableSharedFlow<VoiceCommand>()
    private val _state = MutableStateFlow(VoiceCommandState.UNAVAILABLE)
    private val _errors = MutableSharedFlow<VoiceCommandError>(extraBufferCapacity = 1)
    private var closed = false

    override val commands: Flow<VoiceCommand> = _commands.asSharedFlow()
    override val state: StateFlow<VoiceCommandState> = _state.asStateFlow()
    override val errors: Flow<VoiceCommandError> = _errors.asSharedFlow()

    /**
     * Starting the listener is a lifecycle operation (the UI does it when a destination is
     * resumed), not a user request to issue a voice command. Do not publish an error here: the
     * view model maps INPUT_UNAVAILABLE to an audible alert, which would otherwise sound on
     * every tab change while voice support is not provisioned. The UNAVAILABLE state is already
     * exposed to the UI; an explicit voice action can report an error when that action exists.
     */
    override fun start() = Unit

    override fun stop() = Unit

    override fun close() {
        closed = true
    }
}
