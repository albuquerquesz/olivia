package com.olivia.companion.session

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OliviaMarkersJsonTest {
    @Test
    fun artifactContainsOnlyVersionedMarkersAndSuggestedCuts() {
        val json = OliviaMarkersJson.encode(ScenarioTake.review())

        assertTrue(json.contains("\"version\": 1"))
        assertTrue(json.contains("\"contractVersion\": \"${com.olivia.core.ContractVersion.CURRENT}\""))
        assertTrue(json.contains("\"sessionId\": \"scenario-ensaio\""))
        assertTrue(json.contains("\"durationMs\": 60000"))
        assertTrue(json.contains("\"offsetMs\": 12000"))
        assertTrue(json.contains("\"direction\": \"TURN_RIGHT\""))
        assertTrue(json.contains("\"status\": \"OBEYED\""))
        assertTrue(json.contains("\"suggestedCuts\""))
        assertFalse(json.contains("bytes"))
        assertFalse(json.contains("audio"))
    }

    @Test
    fun artifactEscapesUserFacingLabels() {
        val event = ScenarioTake.events().first().copy(label = "linha \"segura\"\n")
        val review = ScenarioTake.review().copy(timeline = listOf(event))

        val json = OliviaMarkersJson.encode(review)

        assertTrue(json.contains("linha \\\"segura\\\"\\n"))
    }
}
