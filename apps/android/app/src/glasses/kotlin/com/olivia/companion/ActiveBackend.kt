package com.olivia.companion

import com.olivia.core.FrameAnalyzer
import com.olivia.core.GlassesGateway
import com.olivia.core.PostProcessor
import com.olivia.core.RecordingSource
import com.olivia.companion.session.TimelinePostProcessor

object ActiveBackend {
    val current = GlassesBackend(
        id = "glasses",
        label = "Ray-Ban Meta via Device Access Toolkit",
        requiresPhysicalGlasses = true,
        usesDatSdk = true,
    )

    fun createGlassesGateway(): GlassesGateway? = null

    fun createFrameAnalyzer(): FrameAnalyzer? = null

    fun createPostProcessor(): PostProcessor = TimelinePostProcessor()

    /** O conector de gravação nativa permanece atrás do adapter BE-12. */
    fun createRecordingSource(@Suppress("UNUSED_PARAMETER") context: android.content.Context): RecordingSource? = null
}
