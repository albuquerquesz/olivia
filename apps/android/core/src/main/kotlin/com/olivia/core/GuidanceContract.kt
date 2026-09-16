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
    val sessionId: SessionId,
    val issuedAtEpochMs: Long,
    val diagnosis: VisualDiagnosis,
    val direction: Direction,
    val confidence: Confidence,
    /** Texto curto em pt-BR, ex.: "Vire 20 graus à direita". Vazio no modo sinal. */
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
    public fun onAnalysis(analysis: FrameAnalysis, nowEpochMs: Long): Correction?

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

    /** Queda de conexão com os óculos. */
    CONNECTION_LOST,

    /** Retomada da conexão. */
    CONNECTION_RESTORED,

    /** O reconhecedor local não encontrou um dos quatro comandos suportados. */
    VOICE_COMMAND_ERROR,

    /** O reconhecimento local não está disponível neste aparelho/configuração. */
    VOICE_INPUT_UNAVAILABLE,
}

/**
 * Saída de áudio roteada para o alto-falante dos óculos por Bluetooth.
 *
 * Nunca presuma display: áudio é o único canal de retorno para a pessoa.
 */
public interface AudioFeedback {
    public val mode: AudioMode

    public suspend fun announce(correction: Correction)

    public suspend fun signal(signal: SessionSignal)

    public suspend fun stop()
}
