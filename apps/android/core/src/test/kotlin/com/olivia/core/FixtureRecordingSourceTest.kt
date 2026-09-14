package com.olivia.core

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FixtureRecordingSourceTest {
    @Test
    fun `fixture path is unavailable until transfer progress finishes`() = runBlocking {
        val asset = asset()
        val source = FixtureRecordingSource(listOf(FixtureRecording(asset, "/fixture/take.mp4")))

        assertNull(source.localPathOf(asset.id))
        assertEquals(listOf(0L, asset.sizeBytes), source.fetch(asset).toList().map(TransferProgress::transferredBytes))
        assertEquals("/fixture/take.mp4", source.localPathOf(asset.id))
    }

    @Test
    fun `linear alignment refuses automatic cuts beyond tolerance`() {
        val event = TimelineEvent("event", SessionId("s"), 0, 1_000, TimelineEventType.SESSION_DEGRADED)
        val precise = LinearMediaAlignment(event.sessionId, mediaOffsetAtSessionStartMs = 100, uncertaintyMs = 250)
        val uncertain = precise.copy(uncertaintyMs = 251)

        assertEquals(1_100, precise.align(event)?.mediaOffsetMs)
        assertNull(uncertain.align(event))
    }

    private fun asset(): RecordingAsset = RecordingAsset(
        id = "fixture", sessionId = SessionId("s"), startedAtEpochMs = 0,
        durationMs = 10_000, sizeBytes = 100, hasAudioTrack = true,
    )
}
