package com.olivia.companion

import com.olivia.core.RecordingSource

object ActiveBackend {
    val current = GlassesBackend(
        id = "glasses",
        label = "Ray-Ban Meta via Device Access Toolkit",
        // The glasses flavor can run either against physical Ray-Ban Meta hardware or
        // the DAT MockDeviceKit. Keep the UI and runtime on the same source of truth.
        requiresPhysicalGlasses = !BuildConfig.MWDAT_USE_MOCK_DEVICE_KIT,
        usesDatSdk = true,
    )

    /** O conector de gravação nativa permanece atrás do adapter BE-12. */
    fun createRecordingSource(@Suppress("UNUSED_PARAMETER") context: android.content.Context): RecordingSource? = null
}
