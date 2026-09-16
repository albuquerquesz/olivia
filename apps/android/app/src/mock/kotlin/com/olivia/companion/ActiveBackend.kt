package com.olivia.companion

import com.olivia.companion.session.ScenarioFrameAnalyzer
import com.olivia.companion.session.ScenarioGlassesGateway
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

    /** O vídeo nativo só entra quando o adapter BE-12 estiver disponível. */
    fun createRecordingSource(@Suppress("UNUSED_PARAMETER") context: android.content.Context): RecordingSource? = null
}
