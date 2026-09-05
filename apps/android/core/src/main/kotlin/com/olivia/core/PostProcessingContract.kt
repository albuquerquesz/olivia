package com.olivia.core

import kotlinx.coroutines.flow.Flow

/** Referência ao vídeo 3K gravado nos óculos. Nunca carrega os bytes da mídia. */
public data class RecordingAsset(
    val id: String,
    val sessionId: SessionId,
    val startedAtEpochMs: Long,
    val durationMs: Long,
    val sizeBytes: Long,
    val hasAudioTrack: Boolean,
)

public data class TransferProgress(
    val assetId: String,
    val transferredBytes: Long,
    val totalBytes: Long,
)

/**
 * Mídia nativa dos óculos.
 *
 * Só entra em cena depois que a sessão encerra: a transferência é por Wi-Fi e não pode
 * disputar banda nem bateria com o loop ao vivo.
 */
public interface RecordingSource {
    public suspend fun listTakes(sessionId: SessionId): List<RecordingAsset>

    /** Emite progresso e termina quando o arquivo local estiver pronto. */
    public fun fetch(asset: RecordingAsset): Flow<TransferProgress>

    public suspend fun localPathOf(assetId: String): String?
}

public data class TranscriptSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String,
)

public data class SuggestedCut(
    val startMs: Long,
    val endMs: Long,
    val reason: CutReason,
    val confidence: Confidence,
)

public enum class CutReason {
    /** Trecho em que uma correção ficou pendente ou ignorada. */
    UNCORRECTED_DEVIATION,

    /** A sessão estava degradada: sem frame, sem garantia de enquadramento. */
    DEGRADED_STRETCH,

    /** A fala indicou repetição ou erro, ex.: "deixa eu refazer". */
    SPEECH_RETAKE,

    SILENCE,
}

public data class SessionReview(
    val sessionId: SessionId,
    val recording: RecordingAsset?,
    val transcript: List<TranscriptSegment>,
    val suggestedCuts: List<SuggestedCut>,
    val timeline: List<TimelineEvent>,
    val contractVersion: String = ContractVersion.CURRENT,
)

/**
 * Revisão pós-take: transcrição e cortes sugeridos.
 *
 * Roda só depois de [SessionState.STOPPED] e move a sessão por `PROCESSING` até
 * `READY`. A transcrição usa o áudio da gravação, nunca o microfone ao vivo.
 */
public interface PostProcessor {
    public suspend fun process(
        sessionId: SessionId,
        recording: RecordingAsset?,
        timeline: List<TimelineEvent>,
    ): Result<SessionReview>
}
