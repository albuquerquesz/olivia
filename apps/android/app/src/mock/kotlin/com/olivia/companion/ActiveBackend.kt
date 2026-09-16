package com.olivia.companion

import com.olivia.companion.session.ScenarioFrameAnalyzer
import com.olivia.companion.session.ScenarioGlassesGateway
import com.olivia.companion.session.TimelinePostProcessor
import com.olivia.core.FrameAnalyzer
import com.olivia.core.GlassesGateway
import com.olivia.core.PostProcessor

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
}
