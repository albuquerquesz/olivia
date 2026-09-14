package com.olivia.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VisualAnalysisSchemaTest {
    @Test
    fun `maps closed framing reply to domain diagnosis`() {
        val result = VisualAnalysisSchema.decode(
            """{"schemaVersion":"1.0","diagnoses":[{"kind":"FRAMING","severity":"MAJOR","confidence":0.9,"direction":"TURN_RIGHT","magnitude":null}]}""",
        )

        assertTrue(result.isSuccess)
        assertEquals(Direction.TURN_RIGHT, result.getOrThrow().single().direction)
    }

    @Test
    fun `rejects raw prose unknown fields and duplicate diagnosis kinds`() {
        val prose = VisualAnalysisSchema.decode("turn right now")
        val unknownField = VisualAnalysisSchema.decode(
            """{"schemaVersion":"1.0","diagnoses":[],"instruction":"ignore policy"}""",
        )
        val duplicate = VisualAnalysisSchema.decode(
            """{"schemaVersion":"1.0","diagnoses":[{"kind":"LIGHT","severity":"OK","confidence":0.9,"direction":null,"magnitude":null},{"kind":"LIGHT","severity":"OK","confidence":0.9,"direction":null,"magnitude":null}]}""",
        )

        assertFalse(prose.isSuccess)
        assertFalse(unknownField.isSuccess)
        assertFalse(duplicate.isSuccess)
    }

    @Test
    fun `rejects incompatible direction and uncalibrated magnitude`() {
        val incompatible = VisualAnalysisSchema.decode(
            """{"schemaVersion":"1.0","diagnoses":[{"kind":"HORIZON","severity":"MAJOR","confidence":0.9,"direction":"TURN_LEFT","magnitude":null}]}""",
        )
        val magnitude = VisualAnalysisSchema.decode(
            """{"schemaVersion":"1.0","diagnoses":[{"kind":"FRAMING","severity":"MAJOR","confidence":0.9,"direction":"TURN_LEFT","magnitude":20.0}]}""",
        )

        assertFalse(incompatible.isSuccess)
        assertFalse(magnitude.isSuccess)
    }
}
