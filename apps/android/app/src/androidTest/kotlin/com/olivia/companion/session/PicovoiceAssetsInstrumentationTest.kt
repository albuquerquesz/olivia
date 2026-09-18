package com.olivia.companion.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Proves that the local Porcupine and Rhino model bundle is present in the APK. */
@RunWith(AndroidJUnit4::class)
class PicovoiceAssetsInstrumentationTest {
    @Test
    fun portugueseModelsAndContextsArePackaged() {
        val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
        listOf(
            "olivia_android.ppn" to 1_000L,
            "porcupine_params_pt.pv" to 100_000L,
            "olivia-rhino_pt_android_v4_1_0.rhn" to 100L,
            "rhino_params_pt.pv" to 100_000L,
        ).forEach { (name, minimumBytes) ->
            val size = assets.open(name).use { it.available().toLong() }
            assertTrue("$name must be packaged and non-empty", size >= minimumBytes)
        }
    }
}
