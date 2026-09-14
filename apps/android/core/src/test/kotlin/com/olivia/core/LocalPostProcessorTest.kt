package com.olivia.core

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalPostProcessorTest {
    @Test
    fun `keeps timeline and produces aligned cuts only after a local transcript`() = runBlocking {
        val session = SessionId("post")
        val recording = RecordingAsset("asset", session, 0, 20_000, 1_000, true)
        val issue = TimelineEvent("issue", session, 0, 2_000, TimelineEventType.CORRECTION_ISSUED, correctionId = "c")
        val ignored = TimelineEvent("ignored", session, 0, 5_000, TimelineEventType.CORRECTION_RESOLVED,
            correctionId = "c", correctionStatus = CorrectionStatus.IGNORED)
        val processor = LocalPostProcessor(
            transcriptProvider = object : LocalTranscriptProvider {
                override suspend fun transcribe(recording: RecordingAsset): Result<List<TranscriptSegment>> =
                    Result.success(emptyList())
            },
            alignmentFor = { LinearMediaAlignment(session, 0, 100) },
        )

        val review = processor.process(session, recording, listOf(issue, ignored)).getOrThrow()

        assertEquals(listOf(issue, ignored), review.timeline)
        assertEquals(CutReason.UNCORRECTED_DEVIATION, review.suggestedCuts.single().reason)
        assertEquals(2_000, review.suggestedCuts.single().startMs)
        assertEquals(5_000, review.suggestedCuts.single().endMs)
    }

    @Test
    fun `does not invent cuts when media alignment is unavailable`() = runBlocking {
        val session = SessionId("post")
        val recording = RecordingAsset("asset", session, 0, 20_000, 1_000, true)
        val processor = LocalPostProcessor(
            transcriptProvider = object : LocalTranscriptProvider {
                override suspend fun transcribe(recording: RecordingAsset): Result<List<TranscriptSegment>> =
                    Result.success(emptyList())
            },
            alignmentFor = { null },
        )

        val review = processor.process(session, recording, emptyList()).getOrThrow()

        assertTrue(review.suggestedCuts.isEmpty())
    }
}
