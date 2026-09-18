package com.olivia.companion.session

import com.olivia.core.SessionId
import com.olivia.core.Timeline
import com.olivia.core.TimelineEvent

class InMemoryTimeline : Timeline {
    private val bySession = linkedMapOf<String, MutableList<TimelineEvent>>()

    override fun record(event: TimelineEvent) {
        bySession.getOrPut(event.sessionId.value) { mutableListOf() }.add(event)
    }

    override fun events(sessionId: SessionId): List<TimelineEvent> =
        bySession[sessionId.value].orEmpty().toList()

    fun sessionIds(): List<SessionId> = bySession.keys.map(::SessionId)

    fun replace(sessionId: SessionId, events: List<TimelineEvent>) {
        bySession[sessionId.value] = events.toMutableList()
    }

    fun remove(sessionId: SessionId) {
        bySession.remove(sessionId.value)
    }
}
