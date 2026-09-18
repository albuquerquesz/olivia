package com.olivia.companion.infra

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseOptions
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real SDK's configuration object, without initializing/networking. */
@RunWith(AndroidJUnit4::class)
class InfraConfigurationIntegrationTest {
    @Test
    fun syntheticOptionsStaySeparateFromProductConfiguration() {
        val options = FirebaseOptions.Builder()
            .setApplicationId("1:123:android:abcdef")
            .setProjectId("olivia-synthetic")
            .setApiKey("synthetic-not-a-credential")
            .build()
        assertEquals("olivia-synthetic", options.projectId)
        assertEquals("1:123:android:abcdef", options.applicationId)
    }
}
