package com.olivia.core

/** Tracks extra delivery delay without assuming that phone and encoder clocks share an epoch. */
public class SourceBacklog {
    private var bestOffsetMs: Long? = null
    private var previousPtsUs: Long? = null

    public fun observe(presentationTimeUs: Long, arrivalMs: Long): Long {
        val previous = previousPtsUs
        // Zero/repeated PTS occurs at DAT startup; it cannot establish a source clock.
        if (presentationTimeUs <= 0 || presentationTimeUs == previous) return 0
        if (previous != null && presentationTimeUs < previous) bestOffsetMs = null
        previousPtsUs = presentationTimeUs
        val offset = arrivalMs - presentationTimeUs / 1_000
        val best = minOf(bestOffsetMs ?: offset, offset)
        bestOffsetMs = best
        return (offset - best).coerceAtLeast(0)
    }

    public fun reset() {
        bestOffsetMs = null
        previousPtsUs = null
    }
}
