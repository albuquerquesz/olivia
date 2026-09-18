package com.olivia.core

import kotlin.jvm.JvmInline

@JvmInline
public value class SessionId(public val value: String)

/**
 * Estados da sessão.
 *
 * `STARTED`, `PAUSED`, `STOPPED` e `DEGRADED` são invariantes do produto e não podem
 * ser removidos ou renomeados. Os demais existem para tornar explícitas as bordas que
 * a UI precisa mostrar sem inventar estado próprio.
 */
public enum class SessionState {
    IDLE,

    /** Pedido de início enviado; ainda não há frame confirmando captura. */
    STARTING,

    STARTED,
    PAUSED,

    /** Quatro segundos sem frame. A sessão continua viva e pode voltar para `STARTED`. */
    DEGRADED,

    /** Encerramento pedido; a captura ao vivo ainda está sendo fechada. */
    STOPPING,

    /** Loop ao vivo encerrado. Nada mais é analisado a partir daqui. */
    STOPPED,

    /** Pós-processamento rodando: transferência do vídeo nativo, transcrição, cortes. */
    PROCESSING,

    /** Timeline e cortes prontos para revisão. */
    READY,

    ERROR,
}

/**
 * Parâmetros do loop ao vivo. Os defaults são os valores de arquitetura; continuam
 * configuráveis porque bateria e ensaio exigem ajuste fino.
 */
public data class SessionConfig(
    val samplingFps: Double = 0.5,
    val analysisIntervalMs: Long = 2_000,
    val degradedAfterMs: Long = 4_000,
    val correctionCooldownMs: Long = 4_000,
    val audioMode: AudioMode = AudioMode.VOICE,
) {
    init {
        require(samplingFps > 0) { "samplingFps deve ser maior que zero" }
        require(analysisIntervalMs > 0) { "analysisIntervalMs deve ser maior que zero" }
        require(degradedAfterMs > analysisIntervalMs) {
            "degradedAfterMs precisa ser maior que analysisIntervalMs, senão a sessão " +
                "entra em DEGRADED entre duas análises normais"
        }
        require(correctionCooldownMs >= 0) { "correctionCooldownMs não pode ser negativo" }
    }
}

public data class SessionSnapshot(
    val id: SessionId,
    val state: SessionState,
    val startedAtEpochMs: Long,
    val elapsedMs: Long,
    val config: SessionConfig,
    val lastFrameAtEpochMs: Long?,
    val failure: OliviaFailure? = null,
)

public data class OliviaFailure(
    val code: FailureCode,
    val message: String,
)

public enum class FailureCode {
    GLASSES_UNAVAILABLE,
    CAPTURE_FAILED,
    ANALYSIS_FAILED,
    AUDIO_FAILED,
    RECORDING_TRANSFER_FAILED,
    POST_PROCESSING_FAILED,
}
