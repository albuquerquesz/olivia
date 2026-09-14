package com.olivia.core

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class FileTimelineTest {
    @Test
    fun `persists a sanitized event idempotently across repository instances`() {
        val directory = Files.createTempDirectory("olivia-timeline-test-")
        val session = SessionId("take-1")
        val event = TimelineEvent(
            id = "event-1",
            sessionId = session,
            atEpochMs = 1_700_000_000_000,
            offsetMs = 0,
            type = TimelineEventType.SESSION_STARTED,
            label = "iniciou",
        )

        FileTimeline(directory).apply {
            record(event)
            record(event)
        }

        assertEquals(listOf(event), FileTimeline(directory).events(session))
    }
}
