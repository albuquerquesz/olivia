package com.olivia.companion.ai

import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.olivia.companion.BuildConfig

/**
 * Installs Android attestation before Firebase AI Logic is used. The Firebase Console still owns
 * App Check registration, enforcement, key restriction, model access, and quotas; no secret or
 * fallback provider is embedded in the app.
 */
internal object FirebaseAppCheckBootstrap {
    fun install(app: FirebaseApp) {
        val factory = if (BuildConfig.OLIVIA_USE_PLAY_INTEGRITY_APP_CHECK) {
            PlayIntegrityAppCheckProviderFactory.getInstance()
        } else {
            debugFactory()
        }
        FirebaseAppCheck.getInstance(app).installAppCheckProviderFactory(
            factory,
            true,
        )
    }

    private fun debugFactory(): AppCheckProviderFactory =
        Class.forName("com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory")
            .getMethod("getInstance")
            .invoke(null) as AppCheckProviderFactory
}
