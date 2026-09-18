package com.olivia.companion.session

import com.olivia.core.Direction
import com.olivia.core.SessionSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AudioCueTest {
    @Test
    fun everyClosedDirectionMapsToALocalCueAndShortInstruction() {
        Direction.entries.forEach { direction ->
            assertNotNull(cueFor(direction).resourceId)
            assertNotNull(spokenTextFor(direction, magnitude = 20.0))
        }
        assertEquals(AudioCue.DIRECTION_LEFT, cueFor(Direction.TURN_LEFT))
        assertEquals(AudioCue.DIRECTION_RIGHT, cueFor(Direction.TURN_RIGHT))
    }

    @Test
    fun everySessionSignalHasDistinctMappingAndText() {
        SessionSignal.entries.forEach { signal ->
            assertNotNull(cueFor(signal).resourceId)
            assertNotNull(spokenTextFor(signal))
        }
        assertEquals(AudioCue.CONNECTION_LOST, cueFor(SessionSignal.CONNECTION_LOST))
        assertEquals(AudioCue.CONNECTION_RESTORED, cueFor(SessionSignal.CONNECTION_RESTORED))
        assertEquals("Conexão perdida", spokenTextFor(SessionSignal.CONNECTION_LOST))
        assertNotEquals(cueFor(SessionSignal.SESSION_PAUSED), cueFor(SessionSignal.CONNECTION_LOST))
        assertNotEquals(cueFor(SessionSignal.SESSION_PAUSED), cueFor(SessionSignal.CONNECTION_RESTORED))
    }
}
