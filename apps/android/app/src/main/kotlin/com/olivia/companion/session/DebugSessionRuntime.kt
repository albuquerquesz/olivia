package com.olivia.companion.session

import com.olivia.core.SessionConfig
import com.olivia.core.SessionId
import com.olivia.core.SessionSnapshot
import com.olivia.core.SessionState
import com.olivia.core.TimelineEvent
import com.olivia.core.TimelineEventType

/**
 * Controlador de sessão só para a UI de debug do flavor mock.
 * Não substitui a máquina de estados do FE-02: transições inválidas são ignoradas.
 */
class DebugSessionRuntime(
    private val timeline: InMemoryTimeline,
    private val clock: () -> Long,
) {
    private var startedAt: Long = 0
    private var pausedAt: Long? = null
    private var pausedTotal: Long = 0
    private var frozenElapsed: Long = 0

    var snapshot: SessionSnapshot? = null
        private set

    fun elapsed(now: Long = clock()): Long {
        val current = snapshot ?: return 0
        return when (current.state) {
            SessionState.STARTED, SessionState.DEGRADED, SessionState.STARTING ->
                (now - startedAt - pausedTotal).coerceAtLeast(0)
            SessionState.PAUSED ->
                (pausedAt ?: now).let { it - startedAt - pausedTotal }.coerceAtLeast(0)
            SessionState.IDLE,
            SessionState.STOPPING,
            SessionState.STOPPED,
            SessionState.PROCESSING,
            SessionState.READY,
            SessionState.ERROR -> frozenElapsed
        }
    }

    fun start(config: SessionConfig): SessionSnapshot? {
        val state = snapshot?.state
        if (state != null && state != SessionState.IDLE && state != SessionState.STOPPED) {
            return snapshot
        }
        val now = clock()
        startedAt = now
        pausedAt = null
        pausedTotal = 0
        frozenElapsed = 0
        val id = SessionId("session-$now")
        val snap = SessionSnapshot(
            id = id,
            state = SessionState.STARTED,
            startedAtEpochMs = now,
            elapsedMs = 0,
            config = config,
            lastFrameAtEpochMs = now,
        )
        snapshot = snap
        record(id, now, 0, TimelineEventType.SESSION_STARTED, "Sessão iniciada")
        return snap.copy()
    }

    fun pause(): SessionSnapshot? = transition(
        from = setOf(SessionState.STARTED, SessionState.DEGRADED),
        to = SessionState.PAUSED,
        type = TimelineEventType.SESSION_PAUSED,
        label = "Pausa",
    ) { now ->
        pausedAt = now
    }

    fun resume(): SessionSnapshot? = transition(
        from = setOf(SessionState.PAUSED),
        to = SessionState.STARTED,
        type = TimelineEventType.SESSION_RESUMED,
        label = "Retomada",
    ) { now ->
        val pausedSince = pausedAt ?: now
        pausedTotal += now - pausedSince
        pausedAt = null
    }

    fun degrade(): SessionSnapshot? = transition(
        from = setOf(SessionState.STARTED),
        to = SessionState.DEGRADED,
        type = TimelineEventType.SESSION_DEGRADED,
        label = "Queda de conexão",
        lastFrame = { null },
    )

    fun recover(): SessionSnapshot? = transition(
        from = setOf(SessionState.DEGRADED),
        to = SessionState.STARTED,
        type = TimelineEventType.SESSION_RECOVERED,
        label = "Conexão restaurada",
    )

    fun frameReceived(capturedAtEpochMs: Long = clock()): SessionSnapshot? {
        val current = snapshot ?: return null
        if (current.state != SessionState.STARTED && current.state != SessionState.DEGRADED) {
            return snapshot
        }
        val elapsedMs = elapsed(capturedAtEpochMs)
        val next = current.copy(
            state = SessionState.STARTED,
            elapsedMs = elapsedMs,
            lastFrameAtEpochMs = capturedAtEpochMs,
        )
        snapshot = next
        if (current.state == SessionState.DEGRADED) {
            record(
                sessionId = current.id,
                at = capturedAtEpochMs,
                offsetMs = elapsedMs,
                type = TimelineEventType.SESSION_RECOVERED,
                label = "Conexão restaurada",
            )
        }
        return next.copy()
    }

    fun stop(): SessionSnapshot? {
        val current = snapshot ?: return null
        if (current.state !in LIVE_OR_PAUSED) return snapshot
        val now = clock()
        if (current.state == SessionState.PAUSED) {
            val pausedSince = pausedAt ?: now
            pausedTotal += now - pausedSince
            pausedAt = null
        }
        frozenElapsed = elapsed(now)
        val snap = current.copy(
            state = SessionState.STOPPED,
            elapsedMs = frozenElapsed,
        )
        snapshot = snap
        record(
            current.id,
            now,
            frozenElapsed,
            TimelineEventType.SESSION_STOPPED,
            "Sessão encerrada",
        )
        return snap.copy()
    }

    fun view(now: Long = clock()): SessionSnapshot? =
        snapshot?.copy(elapsedMs = elapsed(now))

    private fun transition(
        from: Set<SessionState>,
        to: SessionState,
        type: TimelineEventType,
        label: String,
        lastFrame: (Long) -> Long? = { it },
        extra: (Long) -> Unit = {},
    ): SessionSnapshot? {
        val current = snapshot ?: return null
        if (current.state !in from) return snapshot
        val now = clock()
        extra(now)
        val elapsedMs = elapsed(now)
        val snap = current.copy(
            state = to,
            elapsedMs = elapsedMs,
            lastFrameAtEpochMs = lastFrame(now),
        )
        snapshot = snap
        record(current.id, now, elapsedMs, type, label)
        return snap.copy()
    }

    private fun record(
        sessionId: SessionId,
        at: Long,
        offsetMs: Long,
        type: TimelineEventType,
        label: String,
    ) {
        timeline.record(
            TimelineEvent(
                id = "${sessionId.value}-$type-$at",
                sessionId = sessionId,
                atEpochMs = at,
                offsetMs = offsetMs.coerceAtLeast(0),
                type = type,
                label = label,
            ),
        )
    }

    private companion object {
        val LIVE_OR_PAUSED = setOf(
            SessionState.STARTED,
            SessionState.PAUSED,
            SessionState.DEGRADED,
        )
    }
}
