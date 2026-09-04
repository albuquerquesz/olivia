package com.olivia.companion

object ActiveBackend {
    val current = GlassesBackend(
        id = "glasses",
        label = "Ray-Ban Meta via Device Access Toolkit",
        requiresPhysicalGlasses = true,
        usesDatSdk = true,
    )
}
