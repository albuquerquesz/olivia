package com.olivia.companion

object ActiveBackend {
    val current = GlassesBackend(
        id = "glasses",
        label = "Ray-Ban Meta via Device Access Toolkit",
        // The glasses flavor can run either against physical Ray-Ban Meta hardware or
        // the DAT MockDeviceKit. Keep the UI and runtime on the same source of truth.
        requiresPhysicalGlasses = !BuildConfig.MWDAT_USE_MOCK_DEVICE_KIT,
        usesDatSdk = true,
    )
}
