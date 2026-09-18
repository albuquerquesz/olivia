package com.olivia.core

import kotlinx.serialization.Serializable

public enum class TimelineEventType {
    SESSION_STARTED,
    SESSION_PAUSED,
    SESSION_RESUMED,
    SESSION_DEGRADED,
    SESSION_RECOVERED,
    SESSION_STOPPED,

    /** Diagnóstico da IA, com ou sem desvio. */
    DIAGNOSIS,

    CORRECTION_ISSUED,
    CORRECTION_RESOLVED,

    /** Corte sugerido no pós-processamento, a partir da fala ou das marcas ao vivo. */
    CUT_SUGGESTED,

    ERROR,
}

/**
 * O registro único do que aconteceu no take.
 *
 * Por construção não há frame, áudio bruto nem credencial aqui: a timeline sobrevive
 * à sessão e é o que a pessoa revisa e exporta, então só guarda o que é seguro guardar.
 * [offsetMs] vem do relógio monotônico da sessão. BE-12 deve mapeá-lo ao vídeo
 * nativo antes de sugerir cortes. [atEpochMs] é apenas uma âncora externa.
 */
@Serializable
public data class TimelineEvent(
    val id: String,
    val sessionId: SessionId,
    val atEpochMs: Long,
    /** Milissegundos desde o início da sessão. */
    val offsetMs: Long,
    val type: TimelineEventType,
    val diagnosis: VisualDiagnosis? = null,
    val direction: Direction? = null,
    val confidence: Confidence? = null,
    val correctionStatus: CorrectionStatus? = null,
    val correctionId: String? = null,
    /** Texto curto para a UI. Nunca conteúdo de frame ou de áudio. */
    val label: String? = null,
    val contractVersion: String = ContractVersion.CURRENT,
) {
    init {
        require(offsetMs >= 0) { "offsetMs não pode ser negativo" }
    }
}

public interface Timeline {
    public fun record(event: TimelineEvent)

    public fun events(sessionId: SessionId): List<TimelineEvent>
}
