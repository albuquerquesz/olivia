package com.olivia.core

/**
 * Produces only user-requested post-take artifacts. It never exports frame bytes, credentials,
 * microphone PCM, or hidden provider output. The video itself remains a separate local URI.
 */
public object ReviewExporter {
    public fun reviewJson(review: SessionReview): String = ContractJson.encodeReview(review)

    /** Returns null when no local transcription exists; empty text never becomes a fake subtitle. */
    public fun srt(review: SessionReview): String? {
        if (review.transcript.isEmpty()) return null
        return review.transcript
            .sortedWith(compareBy(TranscriptSegment::startMs, TranscriptSegment::endMs))
            .mapIndexed { index, segment ->
                require(segment.startMs >= 0 && segment.endMs > segment.startMs) { "Invalid transcript segment" }
                "${index + 1}\n${timestamp(segment.startMs)} --> ${timestamp(segment.endMs)}\n${sanitize(segment.text)}"
            }
            .joinToString(separator = "\n\n", postfix = "\n")
    }

    private fun timestamp(valueMs: Long): String {
        val hours = valueMs / 3_600_000
        val minutes = (valueMs % 3_600_000) / 60_000
        val seconds = (valueMs % 60_000) / 1_000
        val milliseconds = valueMs % 1_000
        return "%02d:%02d:%02d,%03d".format(hours, minutes, seconds, milliseconds)
    }

    private fun sanitize(text: String): String = text.replace('\r', ' ').replace('\n', ' ').trim()
}
