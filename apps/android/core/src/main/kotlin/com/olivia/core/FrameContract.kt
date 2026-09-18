package com.olivia.core

import kotlinx.coroutines.flow.Flow

public enum class FrameFormat { JPEG, NV21, RGBA_8888 }

/**
 * Um frame amostrado da câmera dos óculos.
 *
 * Efêmero por contrato: quem recebe classifica e descarta. Não persista [bytes], não
 * escreva em log e não guarde referência depois que a análise retorna — o [toString]
 * é deliberadamente cego ao conteúdo para que um log acidental não vaze imagem.
 */
public class Frame(
    public val sessionId: SessionId,
    public val capturedAtEpochMs: Long,
    public val width: Int,
    public val height: Int,
    public val format: FrameFormat,
    public val origin: FrameOrigin,
    public val bytes: ByteArray,
) {
    override fun toString(): String =
        "Frame(session=${sessionId.value}, ${width}x$height, $format, origin=$origin, " +
            "capturedAt=$capturedAtEpochMs, bytes=${bytes.size})"
}

/** De onde o frame veio. Vai para a instrumentação e para a timeline. */
public enum class FrameOrigin { MOCK, DAT }

public enum class GlassesConnection { CONNECTED, DISCONNECTED, UNAVAILABLE }

/**
 * Captura e conexão com os óculos.
 *
 * É o único ponto que o DAT toca. O adapter de cenário offline e o adapter do DAT implementam esta
 * interface e nada além dela; trocar um pelo outro no dia 18 não deve exigir mudança
 * em sessão, sampler, IA ou UI.
 *
 * Esta interface não conhece [SessionState]: ela reporta conexão e frames crus. Quem
 * decide `DEGRADED` é a máquina de estados da sessão, aplicando
 * [SessionConfig.degradedAfterMs] sobre o silêncio de [frames].
 */
public interface GlassesGateway {
    public val connection: Flow<GlassesConnection>

    /** Frames já amostrados na taxa de [SessionConfig.samplingFps]. */
    public val frames: Flow<Frame>

    public suspend fun startCapture(sessionId: SessionId, config: SessionConfig)

    public suspend fun pauseCapture()

    public suspend fun resumeCapture()

    public suspend fun stopCapture()
}
