package com.olivia.companion

import com.olivia.core.FrameAnalyzer
import com.olivia.core.GlassesGateway

object ActiveBackend {
    val current = GlassesBackend(
        id = "glasses",
        label = "Ray-Ban Meta via Device Access Toolkit",
        requiresPhysicalGlasses = true,
        usesDatSdk = true,
    )

    fun createGlassesGateway(): GlassesGateway? = null

    fun createFrameAnalyzer(): FrameAnalyzer? = null
}
