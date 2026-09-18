package com.olivia.core

import java.nio.file.Files
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FileTimelineTest {
    @Test
    fun `persists a sanitized event idempotently across repository instances`() {
        val directory = Files.createTempDirectory("olivia-timeline-test-")
        try {
            val session = SessionId("take-1")
            val event = event(session, "event-1")

            FileTimeline(directory).apply {
                record(event)
                record(event)
            }

            assertEquals(listOf(event), FileTimeline(directory).events(session))
        } finally {
            Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }

    @Test
    fun `invalid interrupted content never replaces a previously committed timeline`() {
        val directory = Files.createTempDirectory("olivia-timeline-recovery-")
        try {
            val session = SessionId("take-recovery")
            val committed = event(session, "committed")
            FileTimeline(directory).record(committed)
            val target = Files.list(directory).use { files ->
                files.filter { it.fileName.toString().endsWith(".timeline.jsonl") }.findFirst().orElseThrow()
            }
            val bytesBefore = Files.readAllBytes(target)
            // This emulates a leftover temporary write. It must not participate in loading.
            Files.writeString(directory.resolve(".timeline-interrupted.tmp"), "not-json")

            assertEquals(listOf(committed), FileTimeline(directory).events(session))
            assertEquals(bytesBefore.toList(), Files.readAllBytes(target).toList())
        } finally {
            Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }

    @Test
    fun `corrupt committed timeline fails closed instead of returning partial marks`() {
        val directory = Files.createTempDirectory("olivia-timeline-corrupt-")
        try {
            val session = SessionId("take-corrupt")
            FileTimeline(directory).record(event(session, "valid"))
            val target = Files.list(directory).use { files ->
                files.filter { it.fileName.toString().endsWith(".timeline.jsonl") }.findFirst().orElseThrow()
            }
            Files.writeString(target, "{not-json}")

            assertFailsWith<IllegalStateException> { FileTimeline(directory).events(session) }
        } finally {
            Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }

    private fun event(session: SessionId, id: String) = TimelineEvent(
        id = id,
        sessionId = session,
        atEpochMs = 1_700_000_000_000,
        offsetMs = 0,
        type = TimelineEventType.SESSION_STARTED,
        label = "iniciou",
    )
}
