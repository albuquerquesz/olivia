package com.olivia.companion.ai

import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Installs Android attestation before Firebase AI Logic is used. The Firebase Console still owns
 * App Check registration, enforcement, key restriction, model access, and quotas; no secret or
 * fallback provider is embedded in the app.
 */
internal object FirebaseAppCheckBootstrap {
    fun install(app: FirebaseApp) {
        FirebaseAppCheck.getInstance(app).installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance(),
            true,
        )
    }
}
