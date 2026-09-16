package com.olivia.core

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

/**
 * O que a Olivia observa. Fechado de propósito: enquadramento, luz/sombra e horizonte
 * são o escopo do MVP, e a IA não deve devolver categoria fora desta lista.
 */
public enum class DiagnosisKind { FRAMING, LIGHT, HORIZON }

public enum class Severity { OK, MINOR, MAJOR }

@JvmInline
@Serializable
public value class Confidence(public val value: Float) {
    init {
        require(value in 0f..1f) { "confidence precisa estar entre 0 e 1, veio $value" }
    }
}

/** De onde veio o diagnóstico, para a demo conseguir provar o que é IA e o que é mock. */
/** GEMINI_ROI_LOCAL distinguishes cloud localization from local visual diagnostics. */
public enum class AnalyzerSource { MOCK, GEMINI_ROI_LOCAL, TRACKER_LOCAL }

@Serializable
public data class VisualDiagnosis(
    val kind: DiagnosisKind,
    val severity: Severity,
    val confidence: Confidence,
    /** Para onde corrigir. Nulo quando [severity] é [Severity.OK]. */
    val direction: Direction?,
    /** Graus, passos ou stops, conforme a [direction]. Nulo quando não é quantificável. */
    val magnitude: Double? = null,
) {
    init {
        require(direction == null || direction.kind == kind) { "Direção incompatível com diagnóstico" }
        require(severity != Severity.OK || (direction == null && magnitude == null)) {
            "Diagnóstico OK não orienta correção"
        }
        require(magnitude == null || (magnitude.isFinite() && magnitude >= 0 && direction != null)) {
            "Magnitude inválida"
        }
    }
}

public data class FrameAnalysis(
    val frameStamp: FrameStamp,
    val analyzedAt: ClockReading,
    val latencyMs: Long,
    val source: AnalyzerSource,
    val diagnoses: List<VisualDiagnosis>,
) {
    init {
        require(latencyMs >= 0)
        require(analyzedAt.monotonicMs >= frameStamp.receivedAt.monotonicMs)
        require(diagnoses.map { it.kind }.distinct().size == diagnoses.size) { "Diagnósticos duplicados" }
    }

    public val hasDeviation: Boolean get() = diagnoses.any { it.severity != Severity.OK }
}

/** A normalized work-area rectangle returned by the cloud locator, never an action. */
public data class NormalizedRoi(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
) {
    init {
        require(x.isFinite() && y.isFinite() && width.isFinite() && height.isFinite())
        require(x in 0f..1f && y in 0f..1f)
        require(width > 0f && height > 0f)
        require(x + width <= 1f && y + height <= 1f) { "ROI must remain inside frame" }
    }

    public val centerX: Float get() = x + width / 2f
    public val centerY: Float get() = y + height / 2f
}

/** Closed cloud result: Gemini only says whether and where the work area is visible. */
public data class WorkAreaLocalization(
    val targetPresent: Boolean,
    val confidence: Confidence,
    val roi: NormalizedRoi?,
) {
    init {
        require((targetPresent && roi != null) || (!targetPresent && roi == null)) {
            "Target presence and ROI must agree"
        }
    }
}

/** Grayscale pixels held in memory only while local visual metrics are computed. */
public data class LumaFrame(
    val width: Int,
    val height: Int,
    val pixels: ByteArray,
) {
    init {
        require(width > 0 && height > 0)
        require(pixels.size == width * height) { "Unexpected luma buffer size" }
    }

    public fun at(x: Int, y: Int): Int = pixels[y * width + x].toInt() and 0xff

    /**
     * Returns a bounded grayscale view for the fast loop. The resize is area-averaged rather
     * than nearest-neighbour so small edges do not alias into the framing/light metrics. The
     * returned buffer is independent and can be discarded with the frame immediately after
     * analysis.
     */
    public fun downsampled(maxEdge: Int): LumaFrame {
        require(maxEdge > 0)
        val sourceEdge = maxOf(width, height)
        if (sourceEdge <= maxEdge) return this
        val scale = maxEdge.toFloat() / sourceEdge
        val targetWidth = (width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (height * scale).toInt().coerceAtLeast(1)
        val output = ByteArray(targetWidth * targetHeight)
        for (targetY in 0 until targetHeight) {
            val sourceTop = (targetY * height / targetHeight).coerceIn(0, height - 1)
            val sourceBottom = (((targetY + 1) * height + targetHeight - 1) / targetHeight)
                .coerceIn(sourceTop + 1, height)
            for (targetX in 0 until targetWidth) {
                val sourceLeft = (targetX * width / targetWidth).coerceIn(0, width - 1)
                val sourceRight = (((targetX + 1) * width + targetWidth - 1) / targetWidth)
                    .coerceIn(sourceLeft + 1, width)
                var sum = 0
                var count = 0
                for (y in sourceTop until sourceBottom) for (x in sourceLeft until sourceRight) {
                    sum += at(x, y)
                    count++
                }
                output[targetY * targetWidth + targetX] = (sum / count).toByte()
            }
        }
        return LumaFrame(targetWidth, targetHeight, output)
    }
}

/** Android supplies decoded luma; core keeps all policy and math platform-independent. */
public interface LocalVisualAnalyzer {
    public fun analyze(luma: LumaFrame, localization: WorkAreaLocalization): List<VisualDiagnosis>
}

/**
 * Classifica um frame.
 *
 * A implementação recebe o frame, classifica e descarta: não guarda bytes, não os
 * envia para lugar nenhum além do checkpoint de IA e não escreve imagem em log.
 * Falha de rede ou resposta fora do schema devolve [Result.failure] em vez de lançar,
 * porque o loop ao vivo não pode morrer por causa de uma inferência ruim.
 * CancellationException deve propagar; limpar referências em finally também ao cancelar.
 */
public interface FrameAnalyzer {
    public suspend fun analyze(frame: Frame): Result<FrameAnalysis>
}
