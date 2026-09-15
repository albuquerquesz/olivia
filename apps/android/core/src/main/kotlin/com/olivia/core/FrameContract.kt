package com.olivia.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

public enum class FrameFormat { JPEG, NV21, RGBA_8888 }

/**
 * Um frame de captura, ANTES do sampler do domínio.
 *
 * Efêmero por contrato: quem recebe classifica e descarta. Não persista [bytes], não
 * escreva em log e não guarde referência depois que a análise retorna — o [toString]
 * é deliberadamente cego ao conteúdo para que um log acidental não vaze imagem.
 */
public class Frame(
    public val stamp: FrameStamp,
    public val width: Int,
    public val height: Int,
    public val format: FrameFormat,
    public val origin: FrameOrigin,
    public val bytes: ByteArray,
) {
    init {
        require(width > 0 && height > 0) { "Dimensões de frame inválidas" }
    }

    override fun toString(): String =
        "Frame(${width}x$height, $format, origin=$origin, sequence=${stamp.sequence}, bytes=${bytes.size})"
}

/** generation muda também após pausa/retomada; não é o ID do take nativo. */
public data class CaptureId(val sessionId: SessionId, val generation: Long) {
    init { require(generation > 0) }
}

/** receivedAt é a chegada ao app, não uma alegação de timestamp do sensor. */
public data class FrameStamp(val capture: CaptureId, val sequence: Long, val receivedAt: ClockReading) {
    init { require(sequence >= 0) }
}

/** Atualizado no callback de captura, mesmo que o consumidor de frames esteja ocupado. */
public data class CaptureHeartbeat(val capture: CaptureId, val receivedAtMonotonicMs: Long) {
    init { require(receivedAtMonotonicMs >= 0) }
}

/**
 * Fence de resultados tardios. Usar no contexto serializado do coordenador.
 * Pausa, perda de captura e STOP invalidam; retomada abre outra geração. Degradação apenas de
 * análise ou áudio mantém a captura ativa para que a saúde possa ser comprovadamente recuperada.
 */
public class CaptureFence {
    private var generation: Long = 0
    private var active: CaptureId? = null

    public fun open(sessionId: SessionId): CaptureId {
        check(generation < Long.MAX_VALUE) { "Gerações de captura esgotadas" }
        return CaptureId(sessionId, ++generation).also { active = it }
    }

    public fun invalidate() { active = null }

    public fun accepts(stamp: FrameStamp, state: SessionState): Boolean =
        state in setOf(SessionState.STARTED, SessionState.DEGRADED) && stamp.capture == active
}

/** De onde o frame veio. Vai para a instrumentação e para a timeline. */
public enum class FrameOrigin { MOCK, DAT }

public enum class GlassesConnection { CONNECTED, DISCONNECTED, UNAVAILABLE }

/**
 * Captura e conexão com os óculos.
 *
 * É o único ponto que o DAT toca. O adapter mock e o adapter do DAT implementam esta
 * interface e nada além dela; trocar um pelo outro no dia 18 não deve exigir mudança
 * em sessão, sampler, IA ou UI.
 *
 * Esta interface não conhece [SessionState]: ela reporta conexão e frames crus. Quem
 * decide `DEGRADED` é a máquina de estados da sessão, aplicando
 * [SessionConfig.degradedAfterMs] sobre [heartbeat], sem depender da inferência.
 */
public interface GlassesGateway {
    public val connection: Flow<GlassesConnection>

    /**
     * Captura não amostrada, um único consumidor. Sem fila ilimitada nem replay de
     * bytes. Adapter libera buffers descartados; consumidor libera os recebidos.
     */
    public val frames: Flow<Frame>

    public val heartbeat: StateFlow<CaptureHeartbeat?>

    /**
     * Não implica iniciar gravação nativa. Não aplica sampling de inferência.
     * Limpar heartbeat anterior antes de entregar callbacks da nova geração.
     */
    public suspend fun startCapture(capture: CaptureId)

    /** Mantém sessão DAT e monitoramento de conexão vivos; suspende entrega de frames. */
    public suspend fun pauseCapture()

    /** Retomada usa geração nova para não aceitar callbacks anteriores à pausa. */
    public suspend fun resumeCapture(capture: CaptureId)

    public suspend fun stopCapture()
}
