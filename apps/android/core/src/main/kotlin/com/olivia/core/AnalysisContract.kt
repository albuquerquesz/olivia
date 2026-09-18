package com.olivia.core

import kotlin.jvm.JvmInline

/**
 * O que a Olivia observa. Fechado de propósito: enquadramento, luz/sombra e horizonte
 * são o escopo do MVP, e a IA não deve devolver categoria fora desta lista.
 */
public enum class DiagnosisKind { FRAMING, LIGHT, HORIZON }

public enum class Severity { OK, MINOR, MAJOR }

@JvmInline
public value class Confidence(public val value: Float) {
    init {
        require(value in 0f..1f) { "confidence precisa estar entre 0 e 1, veio $value" }
    }
}

/** De onde veio o diagnóstico, para a demonstração distinguir execução offline de IA. */
public enum class AnalyzerSource { MOCK, GEMINI }

public data class VisualDiagnosis(
    val kind: DiagnosisKind,
    val severity: Severity,
    val confidence: Confidence,
    /** Para onde corrigir. Nulo quando [severity] é [Severity.OK]. */
    val direction: Direction?,
    /** Graus, passos ou stops, conforme a [direction]. Nulo quando não é quantificável. */
    val magnitude: Double? = null,
)

public data class FrameAnalysis(
    val sessionId: SessionId,
    val frameCapturedAtEpochMs: Long,
    val analyzedAtEpochMs: Long,
    val latencyMs: Long,
    val source: AnalyzerSource,
    val diagnoses: List<VisualDiagnosis>,
) {
    val hasDeviation: Boolean get() = diagnoses.any { it.severity != Severity.OK }
}

/**
 * Classifica um frame.
 *
 * A implementação recebe o frame, classifica e descarta: não guarda bytes, não os
 * envia para lugar nenhum além do checkpoint de IA e não escreve imagem em log.
 * Falha de rede ou resposta fora do schema devolve [Result.failure] em vez de lançar,
 * porque o loop ao vivo não pode morrer por causa de uma inferência ruim.
 */
public interface FrameAnalyzer {
    public suspend fun analyze(frame: Frame): Result<FrameAnalysis>
}
