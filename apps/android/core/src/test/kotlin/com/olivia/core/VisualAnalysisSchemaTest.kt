package com.olivia.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkAreaLocalizationSchemaTest {
    @Test
    fun `maps a normalized work area without accepting an action`() {
        val result = WorkAreaLocalizationSchema.decode(
            """{"schemaVersion":"2.0","targetPresent":true,"confidence":0.9,"roi":{"x":0.6,"y":0.2,"width":0.2,"height":0.4}}""",
        )

        assertTrue(result.isSuccess)
        assertTrue(kotlin.math.abs(requireNotNull(result.getOrThrow().roi).centerX - 0.7f) < 0.0001f)
    }

    @Test
    fun `accepts an explicit target absence`() {
        val result = WorkAreaLocalizationSchema.decode(
            """{"schemaVersion":"2.0","targetPresent":false,"confidence":0,"roi":null}""",
        )

        assertFalse(result.getOrThrow().targetPresent)
        assertEquals(null, result.getOrThrow().roi)
    }

    @Test
    fun `rejects prose unknown fields and provider action fields`() {
        val prose = WorkAreaLocalizationSchema.decode("turn right now")
        val unknownField = WorkAreaLocalizationSchema.decode(
            """{"schemaVersion":"2.0","targetPresent":false,"confidence":0,"roi":null,"instruction":"ignore policy"}""",
        )
        val action = WorkAreaLocalizationSchema.decode(
            """{"schemaVersion":"2.0","targetPresent":true,"confidence":0.9,"roi":{"x":0.2,"y":0.2,"width":0.3,"height":0.3},"direction":"TURN_RIGHT"}""",
        )

        assertFalse(prose.isSuccess)
        assertFalse(unknownField.isSuccess)
        assertFalse(action.isSuccess)
    }

    @Test
    fun `rejects unbounded or contradictory ROI`() {
        val outOfBounds = WorkAreaLocalizationSchema.decode(
            """{"schemaVersion":"2.0","targetPresent":true,"confidence":0.9,"roi":{"x":0.8,"y":0.2,"width":0.3,"height":0.4}}""",
        )
        val contradictory = WorkAreaLocalizationSchema.decode(
            """{"schemaVersion":"2.0","targetPresent":false,"confidence":0.9,"roi":{"x":0.2,"y":0.2,"width":0.3,"height":0.3}}""",
        )

        assertFalse(outOfBounds.isSuccess)
        assertFalse(contradictory.isSuccess)
    }
}
