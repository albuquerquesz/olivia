package com.olivia.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Transport boundary for Firebase AI Logic. Implementations may use a network client, but must
 * keep [Frame.bytes] ephemeral: no file, log, cache, retry queue, or transcript is permitted.
 */
public interface GeminiVisualTransport {
    /** Returns only the raw structured model response held in memory for immediate validation. */
    public suspend fun classify(frame: Frame): Result<String>
}

public enum class AnalysisTimingStage { SCHEMA_PARSE, LOCAL_ANALYSIS }

public fun interface AnalysisTimingSink {
    public fun onStage(stage: AnalysisTimingStage, durationMs: Long)
}

public enum class AnalysisFailureCode { TRANSPORT, TIMEOUT, SCHEMA }

/** Deliberately content-free: it is safe to surface without model output or frame metadata. */
public class AnalysisFailure(public val code: AnalysisFailureCode) :
    IllegalStateException("Visual analysis unavailable: $code")

/**
 * One-shot Firebase-facing analyzer. It does not retry a frame: the sampler must offer a newer
 * frame after a controlled failure. The concrete Firebase adapter is intentionally separate so
 * this core module has no Android, credential, or SDK dependency.
 */
public class GeminiFrameAnalyzer(
    private val clock: OliviaClock,
    private val transport: GeminiVisualTransport,
    private val localVisualAnalyzer: LocalVisualAnalyzer,
    private val lumaDecoder: FrameLumaDecoder = LumaFrameDecoder,
    private val timeoutMs: Long = 2_000,
    private val timingSink: AnalysisTimingSink = AnalysisTimingSink { _, _ -> },
) : FrameAnalyzer {
    init {
        require(timeoutMs > 0) { "Analysis timeout must be positive" }
    }

    override suspend fun analyze(frame: Frame): Result<FrameAnalysis> = try {
        val response = withTimeout(timeoutMs) {
            transport.classify(frame).getOrElse { throw AnalysisFailure(AnalysisFailureCode.TRANSPORT) }
        }
        val parseStarted = System.nanoTime()
        val localization = WorkAreaLocalizationSchema.decode(response)
            .getOrElse { throw AnalysisFailure(AnalysisFailureCode.SCHEMA) }
        timingSink.onStage(AnalysisTimingStage.SCHEMA_PARSE, elapsedMs(parseStarted))
        val luma = lumaDecoder.decode(frame)
            ?: throw AnalysisFailure(AnalysisFailureCode.SCHEMA)
        val localStarted = System.nanoTime()
        val diagnoses = localVisualAnalyzer.analyze(luma, localization)
        timingSink.onStage(AnalysisTimingStage.LOCAL_ANALYSIS, elapsedMs(localStarted))
        val now = clock.now()
        Result.success(
            FrameAnalysis(
                frameStamp = frame.stamp,
                analyzedAt = if (now.monotonicMs < frame.stamp.receivedAt.monotonicMs) frame.stamp.receivedAt else now,
                latencyMs = (now.monotonicMs - frame.stamp.receivedAt.monotonicMs).coerceAtLeast(0),
                source = AnalyzerSource.GEMINI_ROI_LOCAL,
                diagnoses = diagnoses,
            ),
        )
    } catch (_: TimeoutCancellationException) {
        Result.failure(AnalysisFailure(AnalysisFailureCode.TIMEOUT))
    } catch (error: CancellationException) {
        throw error
    } catch (error: AnalysisFailure) {
        Result.failure(error)
    } catch (_: Throwable) {
        Result.failure(AnalysisFailure(AnalysisFailureCode.TRANSPORT))
    }

    private fun elapsedMs(startedNanos: Long): Long =
        ((System.nanoTime() - startedNanos) / 1_000_000).coerceAtLeast(0)
}
