package com.olivia.core

import kotlin.test.Test
import kotlin.test.assertEquals

class SourceBacklogTest {
    @Test fun `different epochs do not create artificial delay`() {
        val clock = SourceBacklog()
        assertEquals(0L, clock.observe(90_000_000, 1_000))
        assertEquals(0L, clock.observe(90_100_000, 1_100))
        assertEquals(500L, clock.observe(90_200_000, 1_700))
    }

    @Test fun `startup backlog can drain and new delay is measured from fastest delivery`() {
        val clock = SourceBacklog()
        assertEquals(0L, clock.observe(1_000_000, 5_000))
        assertEquals(0L, clock.observe(2_000_000, 5_100))
        assertEquals(300L, clock.observe(3_000_000, 6_400))
        assertEquals(0L, clock.observe(4_000_000, 7_100))
    }

    @Test fun `restart and repeated startup timestamps do not poison baseline`() {
        val clock = SourceBacklog()
        assertEquals(0L, clock.observe(0, 1_000))
        assertEquals(0L, clock.observe(1_000_000, 2_000))
        assertEquals(0L, clock.observe(1_000_000, 9_000))
        clock.reset()
        assertEquals(0L, clock.observe(2_000_000, 10_000))
        assertEquals(0L, clock.observe(100_000, 11_000))
    }
}
