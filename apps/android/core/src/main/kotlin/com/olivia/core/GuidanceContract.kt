package com.olivia.core

/** Direção da correção entregue à pessoa. Cada valor pertence a um [DiagnosisKind]. */
public enum class Direction(public val kind: DiagnosisKind) {
    TURN_LEFT(DiagnosisKind.FRAMING),
    TURN_RIGHT(DiagnosisKind.FRAMING),
    MOVE_CLOSER(DiagnosisKind.FRAMING),
    MOVE_BACK(DiagnosisKind.FRAMING),
    RAISE(DiagnosisKind.FRAMING),
    LOWER(DiagnosisKind.FRAMING),

    FACE_THE_LIGHT(DiagnosisKind.LIGHT),
    AVOID_BACKLIGHT(DiagnosisKind.LIGHT),
    LEAVE_THE_SHADOW(DiagnosisKind.LIGHT),

    LEVEL_HORIZON_LEFT(DiagnosisKind.HORIZON),
    LEVEL_HORIZON_RIGHT(DiagnosisKind.HORIZON),
}

public enum class CorrectionStatus {
    /** Emitida, ainda sem frame novo que confirme ou negue. */
    PENDING,

    /** O frame seguinte mostrou o desvio resolvido: é isso que vira marca na timeline. */
    OBEYED,

    /** O desvio continua depois da janela de espera. */
    IGNORED,

    /** A sessão pausou, degradou ou encerrou antes de dar para avaliar. */
    EXPIRED,

    /** Um desvio mais grave chegou antes e assumiu o lugar desta. */
    SUPERSEDED,
}

/** Uma instrução pronta para virar áudio. */
public data class Correction(
    val id: String,
    val capture: CaptureId,
    val issuedAt: ClockReading,
    val diagnosis: VisualDiagnosis,
    val direction: Direction,
    val confidence: Confidence,
    /** Texto curto e qualitativo em pt-BR, ex.: "Vire um pouco para a direita". Vazio no modo sinal. */
    val spokenText: String,
    val status: CorrectionStatus = CorrectionStatus.PENDING,
)

/**
 * Decide se um diagnóstico vira correção.
 *
 * Aplica thresholds e o cooldown de [SessionConfig.correctionCooldownMs] para não
 * transformar o loop em spam; devolve nulo quando o certo é ficar calado.
 */
public interface GuidanceEngine {
    public fun onAnalysis(analysis: FrameAnalysis, now: ClockReading): Correction?

    public fun onCorrectionOutcome(correctionId: String, status: CorrectionStatus)
}

public enum class AudioMode {
    /** Android TextToSpeech. */
    VOICE,

    /** WAV local, sem síntese: mais barato em bateria e imune a falha de TTS. */
    SIGNAL,
}

/** Avisos que não são correção de enquadramento. */
public enum class SessionSignal {
    SESSION_STARTED,
    SESSION_PAUSED,
    SESSION_RESUMED,
    SESSION_STOPPED,
    ANALYSIS_UNAVAILABLE,
    ANALYSIS_RESTORED,
    AUDIO_UNAVAILABLE,
    AUDIO_RESTORED,

    /** Queda de conexão com os óculos. */
    CONNECTION_LOST,

    /** Retomada da conexão. */
    CONNECTION_RESTORED,

    /** O reconhecedor local não encontrou um dos quatro comandos suportados. */
    VOICE_COMMAND_ERROR,

    /** O reconhecimento local não está disponível neste aparelho/configuração. */
    VOICE_INPUT_UNAVAILABLE,

    /** A peça voltou a ficar bem enquadrada depois de um desvio; confirmação positiva, não correção. */
    TARGET_FRAMED,
}

/**
 * Saída de áudio roteada para o alto-falante dos óculos por Bluetooth.
 *
 * Nunca presuma display: áudio é o único canal de retorno para a pessoa.
 * Falhas operacionais retornam Result.failure; CancellationException propaga.
 */
public interface AudioFeedback {
    public val mode: AudioMode

    /** Sucesso significa callback de início, não apenas pedido TTS enfileirado. */
    public suspend fun announce(correction: Correction): Result<AudioReceipt>

    public suspend fun signal(signal: SessionSignal): Result<ClockReading>

    public suspend fun stop()
}

/** Callback Android não comprova instante acústico nos óculos. */
public data class AudioReceipt(
    val correctionId: String,
    val capture: CaptureId,
    val startedAt: ClockReading,
)
