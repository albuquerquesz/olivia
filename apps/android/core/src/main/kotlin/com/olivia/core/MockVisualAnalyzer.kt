package com.olivia.core

import kotlinx.coroutines.delay

/** A provenance record for a synthetic or explicitly consented visual fixture. */
public data class VisualFixtureManifest(
    val id: String,
    val source: String,
    val licensedForTesting: Boolean,
    val width: Int,
    val height: Int,
    val expectedDiagnoses: List<VisualDiagnosis>,
) {
    init {
        require(id.isNotBlank())
        require(source.isNotBlank())
        require(width > 0 && height > 0)
        require(expectedDiagnoses.map { it.kind }.distinct().size == expectedDiagnoses.size) {
            "Fixture não pode ter diagnósticos duplicados"
        }
    }
}

/** A deterministic reply selected by the monotonically increasing frame sequence. */
public sealed interface MockAnalysisReply {
    public data class Diagnosis(
        val diagnoses: List<VisualDiagnosis>,
        val latencyMs: Long = 0,
    ) : MockAnalysisReply {
        init {
            require(latencyMs >= 0)
            require(diagnoses.map { it.kind }.distinct().size == diagnoses.size) {
                "Mock não pode devolver diagnósticos duplicados"
            }
        }
    }

    public data class Failure(val message: String, val latencyMs: Long = 0) : MockAnalysisReply {
        init {
            require(message.isNotBlank())
            require(latencyMs >= 0)
        }
    }
}

/**
 * Offline analyzer used by the demo and integration tests.
 *
 * It never inspects, stores or logs [Frame.bytes]. Delay is cancellable, so cancellation stays a
 * cancellation instead of being converted into a normal mock failure.
 */
public class MockFrameAnalyzer(
    private val clock: OliviaClock,
    repliesBySequence: Map<Long, MockAnalysisReply>,
) : FrameAnalyzer {
    private val replies: Map<Long, MockAnalysisReply> = repliesBySequence.toMap()

    override suspend fun analyze(frame: Frame): Result<FrameAnalysis> {
        val reply = replies[frame.stamp.sequence]
            ?: MockAnalysisReply.Diagnosis(emptyList())
        val latency = when (reply) {
            is MockAnalysisReply.Diagnosis -> reply.latencyMs
            is MockAnalysisReply.Failure -> reply.latencyMs
        }
        if (latency > 0) delay(latency)
        return when (reply) {
            is MockAnalysisReply.Failure -> Result.failure(IllegalStateException(reply.message))
            is MockAnalysisReply.Diagnosis -> {
                val analyzedAt = clock.now()
                Result.success(
                    FrameAnalysis(
                        frameStamp = frame.stamp,
                        analyzedAt = if (analyzedAt.monotonicMs < frame.stamp.receivedAt.monotonicMs) {
                            frame.stamp.receivedAt
                        } else {
                            analyzedAt
                        },
                        latencyMs = maxOf(
                            reply.latencyMs,
                            analyzedAt.monotonicMs - frame.stamp.receivedAt.monotonicMs,
                        ),
                        source = AnalyzerSource.MOCK,
                        diagnoses = reply.diagnoses,
                    ),
                )
            }
        }
    }
}
