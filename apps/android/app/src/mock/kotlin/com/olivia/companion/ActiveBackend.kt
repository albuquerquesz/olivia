package com.olivia.companion

import com.olivia.companion.session.ScenarioFrameAnalyzer
import com.olivia.companion.session.ScenarioGlassesGateway
import com.olivia.companion.session.ScenarioRecordingSource
import com.olivia.companion.session.TimelinePostProcessor
import com.olivia.core.FrameAnalyzer
import com.olivia.core.GlassesGateway
import com.olivia.core.PostProcessor
import com.olivia.core.RecordingSource

object ActiveBackend {
    val current = GlassesBackend(
        id = "mock",
        label = "Óculos simulados",
        requiresPhysicalGlasses = false,
        usesDatSdk = false,
    )

    fun createGlassesGateway(): GlassesGateway = ScenarioGlassesGateway()

    fun createFrameAnalyzer(): FrameAnalyzer = ScenarioFrameAnalyzer()

    fun createPostProcessor(): PostProcessor = TimelinePostProcessor()

    /** Fixture local para demonstrar o handoff sem fingir que ela veio dos óculos. */
    fun createRecordingSource(context: android.content.Context): RecordingSource =
        ScenarioRecordingSource(context)
}
