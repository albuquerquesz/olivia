package com.olivia.core

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest

/**
 * Versioned local timeline storage. The Android adapter supplies an app-private directory.
 *
 * One session maps to one newline-delimited JSON file. Rewriting a complete temporary sibling
 * and then moving it atomically prevents a crash from corrupting previously confirmed marks.
 * The filename is a SHA-256 digest of SessionId so arbitrary IDs never become a filesystem path.
 */
public class FileTimeline(private val directory: Path) : Timeline {
    private val loaded: MutableMap<SessionId, MutableList<TimelineEvent>> = mutableMapOf()

    init {
        Files.createDirectories(directory)
    }

    @Synchronized
    override fun record(event: TimelineEvent) {
        val events = load(event.sessionId)
        if (events.any { it.id == event.id }) return
        events += event
        try {
            persist(event.sessionId, events)
        } catch (error: Exception) {
            events.removeAt(events.lastIndex)
            throw IllegalStateException("Não foi possível gravar a timeline local", error)
        }
    }

    @Synchronized
    override fun events(sessionId: SessionId): List<TimelineEvent> = load(sessionId).toList()

    private fun load(sessionId: SessionId): MutableList<TimelineEvent> = loaded.getOrPut(sessionId) {
        val target = fileFor(sessionId)
        if (!Files.exists(target)) return@getOrPut mutableListOf()
        try {
            Files.readAllLines(target, UTF_8)
                .asSequence()
                .filter { it.isNotBlank() }
                .map(ContractJson::decodeEvent)
                .onEach { require(it.sessionId == sessionId) { "Timeline de outra sessão" } }
                .toMutableList()
        } catch (error: Exception) {
            throw IllegalStateException("Timeline local inválida", error)
        }
    }

    private fun persist(sessionId: SessionId, events: List<TimelineEvent>) {
        val target = fileFor(sessionId)
        val temporary = Files.createTempFile(directory, ".timeline-", ".tmp")
        try {
            // Files.writeString is a Java 11 convenience API and is absent from Android's
            // runtime. java.io is available on every supported Android API; the completed
            // temporary file is still atomically renamed below.
            temporary.toFile().outputStream().bufferedWriter(UTF_8).use { writer ->
                writer.write(events.joinToString(separator = "\n") { ContractJson.encodeEvent(it) })
            }
            try {
                Files.move(temporary, target, ATOMIC_MOVE, REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, target, REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun fileFor(sessionId: SessionId): Path = directory.resolve("${sha256(sessionId.value)}.timeline.jsonl")

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
