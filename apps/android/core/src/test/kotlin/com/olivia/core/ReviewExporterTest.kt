package com.olivia.core

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReviewExporterTest {
    @Test
    fun `exports numbered sanitized SRT only when a local transcript exists`() {
        val review = reviewWith(listOf(TranscriptSegment(1_000, 2_250, "primeira\nlinha")))

        val srt = ReviewExporter.srt(review)

        assertContains(requireNotNull(srt), "1\n00:00:01,000 --> 00:00:02,250\nprimeira linha")
    }

    @Test
    fun `does not claim subtitles when transcription is unavailable`() {
        assertNull(ReviewExporter.srt(reviewWith(emptyList())))
    }

    @Test
    fun `review JSON remains contract versioned`() {
        val json = ReviewExporter.reviewJson(reviewWith(emptyList()))
        assertEquals(ContractVersion.CURRENT, ContractJson.decodeReview(json).contractVersion)
    }

    private fun reviewWith(transcript: List<TranscriptSegment>): SessionReview {
        val session = SessionId("export")
        return SessionReview(
            sessionId = session,
            recording = RecordingAsset("asset", session, 0, 3_000, 1, true),
            transcript = transcript,
            suggestedCuts = emptyList(),
            timeline = emptyList(),
        )
    }
}
