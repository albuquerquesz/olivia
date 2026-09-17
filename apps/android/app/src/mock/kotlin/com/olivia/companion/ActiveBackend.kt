package com.olivia.companion

import com.olivia.companion.session.ScenarioRecordingSource
import com.olivia.core.RecordingSource

object ActiveBackend {
    val current = GlassesBackend(
        id = "mock",
        label = "Óculos simulados",
        requiresPhysicalGlasses = false,
        usesDatSdk = false,
    )

    /** Fixture local para demonstrar o handoff sem fingir que ela veio dos óculos. */
    fun createRecordingSource(context: android.content.Context): RecordingSource =
        ScenarioRecordingSource(context)
}
