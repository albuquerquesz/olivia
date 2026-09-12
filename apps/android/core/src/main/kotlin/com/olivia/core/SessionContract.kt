package com.olivia.core

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

@JvmInline
@Serializable
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
 * configuráveis porque bateria e demo exigem ajuste fino.
 */
public data class SessionConfig(
    val analysisIntervalMs: Long = 2_000,
    val degradedAfterMs: Long = 4_000,
    val correctionCooldownMs: Long = 4_000,
    val audioMode: AudioMode = AudioMode.VOICE,
) {
    /** Valor derivado: não existem dois knobs independentes para a mesma cadência. */
    public val samplingFps: Double get() = 1_000.0 / analysisIntervalMs

    init {
        require(analysisIntervalMs > 0) { "analysisIntervalMs deve ser maior que zero" }
        // O watchdog observa a captura, não o sampler ou a conclusão da IA.
        require(degradedAfterMs > 0) { "degradedAfterMs deve ser maior que zero" }
        require(correctionCooldownMs >= 0) { "correctionCooldownMs não pode ser negativo" }
    }
}

public data class SessionSnapshot(
    val id: SessionId,
    val state: SessionState,
    val startedAtEpochMs: Long,
    val elapsedMs: Long,
    val config: SessionConfig,
    /** Âncora externa para exibição; watchdog usa CaptureHeartbeat monotônico. */
    val lastFrameAtEpochMs: Long?,
    val failure: OliviaFailure? = null,
    val degradationCauses: Set<DegradationCause> = emptySet(),
    /** Intenção a restaurar quando TODAS as causas forem resolvidas. */
    val resumeState: SessionState = SessionState.STARTED,
) {
    init {
        require(elapsedMs >= 0)
        require(resumeState == SessionState.STARTED || resumeState == SessionState.PAUSED)
        require((state == SessionState.DEGRADED) == degradationCauses.isNotEmpty()) {
            "DEGRADED exige causa; outros estados não retêm causas ativas"
        }
    }
}

public enum class DegradationCause { CAPTURE_UNAVAILABLE, ANALYSIS_UNAVAILABLE, AUDIO_UNAVAILABLE }

/**
 * Grafo de transições, não um executor de efeitos. O coordenador (FE-02) verifica
 * causas, intenção e callbacks antes de aplicar uma aresta permitida.
 */
public object SessionTransitions {
    public fun allows(from: SessionState, to: SessionState): Boolean = to in when (from) {
        SessionState.IDLE -> setOf(SessionState.STARTING)
        SessionState.STARTING -> setOf(SessionState.STARTED, SessionState.STOPPING, SessionState.ERROR)
        SessionState.STARTED -> setOf(SessionState.PAUSED, SessionState.DEGRADED, SessionState.STOPPING)
        SessionState.PAUSED -> setOf(SessionState.STARTED, SessionState.DEGRADED, SessionState.STOPPING)
        SessionState.DEGRADED -> setOf(SessionState.STARTED, SessionState.PAUSED, SessionState.STOPPING)
        SessionState.STOPPING -> setOf(SessionState.STOPPED, SessionState.ERROR)
        SessionState.STOPPED -> setOf(SessionState.PROCESSING, SessionState.STARTING)
        SessionState.PROCESSING -> setOf(SessionState.READY, SessionState.ERROR, SessionState.STOPPED)
        SessionState.READY -> setOf(SessionState.STARTING)
        SessionState.ERROR -> setOf(SessionState.IDLE)
    }
}

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
