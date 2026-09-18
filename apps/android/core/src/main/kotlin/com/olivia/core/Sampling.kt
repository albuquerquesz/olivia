package com.olivia.core

/** Why a frame was not handed to the analyzer. The caller must release it. */
public enum class FrameDiscardReason {
    INACTIVE_CAPTURE,
    EXPIRED,
    REPLACED_BY_NEWER_FRAME,
}

/**
 * Result of offering a frame to [FrameSampler].
 *
 * The sampler never keeps an unbounded queue. A caller receives every discarded frame so a
 * camera adapter can return its buffer immediately; [Frame] itself deliberately does not own a
 * platform buffer because :core must stay independent of DAT and Android.
 */
public sealed interface SamplingDecision {
    public data class Analyze(val frame: Frame) : SamplingDecision

    public data class Discard(val frame: Frame, val reason: FrameDiscardReason) : SamplingDecision

    public data object Waiting : SamplingDecision
}

/**
 * Single-flight sampler for the live visual loop.
 *
 * At most one frame is being analyzed and one newer frame is retained. The host calls [offer]
 * from the capture callback, [completeAnalysis] from a `finally` block, and [poll] from its
 * monotonic scheduler. This separation makes cadence deterministic in JVM tests and prevents a
 * slow provider from creating a coroutine or byte-buffer backlog.
 */
public class FrameSampler(
    private val clock: OliviaClock,
    private val analysisIntervalMs: Long = 2_000,
    private val maxFrameAgeMs: Long = 2_000,
) {
    private var lastAnalysisStartedAtMs: Long? = null
    private var analysisInFlight: Boolean = false
    private var pending: Frame? = null

    init {
        require(analysisIntervalMs > 0) { "analysisIntervalMs deve ser maior que zero" }
        require(maxFrameAgeMs > 0) { "maxFrameAgeMs deve ser maior que zero" }
    }

    /** Offers a raw frame. [accepts] must use the session's CaptureFence. */
    public fun offer(frame: Frame, accepts: (FrameStamp) -> Boolean): List<SamplingDecision> {
        if (!accepts(frame.stamp)) {
            return listOf(SamplingDecision.Discard(frame, FrameDiscardReason.INACTIVE_CAPTURE))
        }
        if (isExpired(frame, clock.now())) {
            return listOf(SamplingDecision.Discard(frame, FrameDiscardReason.EXPIRED))
        }
        if (analysisInFlight || !cadenceReady(clock.now())) return retainNewest(frame)
        return start(frame)
    }

    /**
     * Marks the current analysis as finished regardless of success, error or cancellation.
     * Calling this from `finally` is what makes the next fresh frame eligible after provider
     * failures without retaining the failed frame.
     */
    public fun completeAnalysis(): List<SamplingDecision> {
        analysisInFlight = false
        return poll()
    }

    /** Advances a pending fresh frame when the cadence allows it. */
    public fun poll(): List<SamplingDecision> {
        if (analysisInFlight) return listOf(SamplingDecision.Waiting)
        val candidate = pending ?: return listOf(SamplingDecision.Waiting)
        pending = null
        val now = clock.now()
        if (isExpired(candidate, now)) {
            return listOf(SamplingDecision.Discard(candidate, FrameDiscardReason.EXPIRED))
        }
        if (!cadenceReady(now)) {
            pending = candidate
            return listOf(SamplingDecision.Waiting)
        }
        return start(candidate)
    }

    /** Invalidates and returns the only buffered frame, if one exists. */
    public fun cancel(): List<SamplingDecision> {
        analysisInFlight = false
        val candidate = pending ?: return emptyList()
        pending = null
        return listOf(SamplingDecision.Discard(candidate, FrameDiscardReason.INACTIVE_CAPTURE))
    }

    private fun retainNewest(frame: Frame): List<SamplingDecision> {
        val replaced = pending
        pending = frame
        return if (replaced == null) {
            listOf(SamplingDecision.Waiting)
        } else {
            listOf(
                SamplingDecision.Discard(replaced, FrameDiscardReason.REPLACED_BY_NEWER_FRAME),
                SamplingDecision.Waiting,
            )
        }
    }

    private fun start(frame: Frame): List<SamplingDecision> {
        analysisInFlight = true
        lastAnalysisStartedAtMs = clock.now().monotonicMs
        return listOf(SamplingDecision.Analyze(frame))
    }

    private fun cadenceReady(now: ClockReading): Boolean =
        lastAnalysisStartedAtMs?.let { now.monotonicMs - it >= analysisIntervalMs } ?: true

    private fun isExpired(frame: Frame, now: ClockReading): Boolean =
        frame.stamp.ageMs(now) > maxFrameAgeMs
}
