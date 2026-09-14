package com.olivia.core

/** Boundary for an on-device Whisper implementation; it receives a post-take asset only. */
public interface LocalTranscriptProvider {
    public suspend fun transcribe(recording: RecordingAsset): Result<List<TranscriptSegment>>
}

/**
 * Produces a review from an already imported asset. Session state transitions and file import are
 * intentionally owned by Android adapters; this component cannot start during the live loop.
 */
public class LocalPostProcessor(
    private val transcriptProvider: LocalTranscriptProvider,
    private val alignmentFor: (RecordingAsset) -> LinearMediaAlignment?,
    private val cutPlanner: CutPlanner = CutPlanner(),
) : PostProcessor {
    override suspend fun process(
        sessionId: SessionId,
        recording: RecordingAsset?,
        timeline: List<TimelineEvent>,
    ): Result<SessionReview> = runCatching {
        require(timeline.all { it.sessionId == sessionId }) { "Timeline from another session" }
        if (recording == null) {
            return@runCatching SessionReview(sessionId, null, emptyList(), emptyList(), timeline)
        }
        require(recording.sessionId == sessionId) { "Recording from another session" }
        val transcript = transcriptProvider.transcribe(recording).getOrElse { throw PostProcessingFailure }
        val alignment = alignmentFor(recording)
        val alignedTimeline = alignment?.takeIf { it.sessionId == sessionId }
            ?.let { mapper -> timeline.mapNotNull { mapper.align(it) } }
            .orEmpty()
        val cuts = if (alignment == null) emptyList() else cutPlanner.plan(recording.durationMs, transcript, alignedTimeline)
        SessionReview(sessionId, recording, transcript, cuts, timeline)
    }

    private object PostProcessingFailure : IllegalStateException("Local transcription unavailable")
}
