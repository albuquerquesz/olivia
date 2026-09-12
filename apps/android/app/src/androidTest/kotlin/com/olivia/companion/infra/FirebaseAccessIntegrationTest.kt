package com.olivia.companion.infra

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.ai.FirebaseAI
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.ServerException
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.appcheck.AppCheckProvider
import com.google.firebase.appcheck.AppCheckToken
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.testing.DebugAppCheckTestHelper
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Three synthetic requests: authorized, rejected App Check, authorized again.
 * Infrastructure probe only; does not implement BE-03 classification or measure hardware.
 */
@RunWith(AndroidJUnit4::class)
class FirebaseAccessIntegrationTest {
    private val arguments = InstrumentationRegistry.getArguments()

    private fun required(key: String): String =
        arguments.getString(key)?.takeIf { it.isNotBlank() }
            ?: throw AssertionError("LIVE_CONFIG_MISSING")

    @Test
    fun authorizedAccessAndAppCheckEnforcement() {
        if (required("oliviaLiveAiEnabled") != "true") throw AssertionError("LIVE_NOT_ENABLED")
        if (required("oliviaAiModel") != "gemini-3.5-flash-lite") throw AssertionError("MODEL_NOT_APPROVED")
        required("firebaseAppCheckDebugSecret")
        // Discard SDK exception causes/messages: JUnit XML must not contain provider payloads.
        try {
            probe(valid = true)
            probe(valid = false)
            probe(valid = true)
        } catch (_: Exception) {
            throw AssertionError("FIREBASE_ACCESS_PROBE_FAILED")
        }
    }

    private fun probe(valid: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().context
        val app = FirebaseApp.initializeApp(context, FirebaseOptions.Builder()
            .setProjectId(required("oliviaFirebaseProjectId"))
            .setApplicationId(required("oliviaFirebaseAppId"))
            .setApiKey(required("oliviaFirebaseApiKey"))
            .build(), "infra-${UUID.randomUUID()}")
        try {
            FirebaseAppCheck.getInstance(app).setTokenAutoRefreshEnabled(false)
            if (valid) {
                DebugAppCheckTestHelper.fromInstrumentationArgs().withDebugProvider(app) {
                    request(app, expectRejection = false)
                }
            } else {
                // Different app instance prevents a previously cached valid token masking rejection.
                FirebaseAppCheck.getInstance(app).installAppCheckProviderFactory {
                    AppCheckProvider {
                        Tasks.forResult<AppCheckToken>(object : AppCheckToken() {
                            override fun getToken(): String = "invalid-synthetic-app-check-token"
                            override fun getExpireTimeMillis(): Long = System.currentTimeMillis() + 60_000
                        })
                    }
                }
                request(app, expectRejection = true)
            }
        } finally { app.delete() }
    }

    private fun request(app: FirebaseApp, expectRejection: Boolean) = runBlocking {
        val model = FirebaseAI.getInstance(app, GenerativeBackend.googleAI()).generativeModel(
            modelName = required("oliviaAiModel"),
            generationConfig = generationConfig { maxOutputTokens = 8; temperature = 0f },
        )
        try {
            val response = withTimeout(30_000) { model.generateContent("Responda somente OK.") }
            if (expectRejection) throw AssertionError("APP_CHECK_NOT_ENFORCED")
            if (response.text.isNullOrBlank()) throw AssertionError("EMPTY_MODEL_RESPONSE")
        } catch (error: ServerException) {
            // Timeout, quota, model unavailable and generic 5xx are NOT evidence of rejection.
            val message = error.message.orEmpty()
            val denied = Regex("(?i)(\\b401\\b|\\b403\\b|PERMISSION_DENIED|UNAUTHENTICATED)").containsMatchIn(message)
            val appCheck = Regex("(?i)app.?check|attestation").containsMatchIn(message)
            if (!expectRejection || !denied || !appCheck) throw AssertionError("UNEXPECTED_PROVIDER_FAILURE")
        }
    }
}
