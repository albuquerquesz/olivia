package com.olivia.companion

object ActiveBackend {
    val current = GlassesBackend(
        id = "mock",
        label = "Óculos simulados",
        requiresPhysicalGlasses = false,
        usesDatSdk = false,
    )
}
