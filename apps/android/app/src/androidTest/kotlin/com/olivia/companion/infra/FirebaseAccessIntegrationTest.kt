package com.olivia.companion.infra

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.FirebaseAI
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.testing.DebugAppCheckTestHelper
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import java.util.UUID

/** Authorized Firebase AI request from the registered target app, using a debug App Check token.
 * This is infrastructure evidence only; it does not implement BE-03 or measure hardware.
 */
@RunWith(AndroidJUnit4::class)
class FirebaseAccessIntegrationTest {
    private val arguments = InstrumentationRegistry.getArguments()
    private val debugAppCheck = DebugAppCheckTestHelper.fromInstrumentationArgs()

    private fun required(key: String): String =
        arguments.getString(key)?.takeIf { it.isNotBlank() }
            ?: throw AssertionError("LIVE_CONFIG_MISSING")

    @Test
    fun authorizedAccessAndAppCheckEnforcement() {
        if (required("oliviaLiveAiEnabled") != "true") throw AssertionError("LIVE_NOT_ENABLED")
        if (required("oliviaAiModel") != "gemini-3.5-flash-lite") throw AssertionError("MODEL_NOT_APPROVED")
        // The test helper reads this same runner argument. Check its presence without ever
        // copying the secret into a report or log.
        required("firebaseAppCheckDebugSecret")
        var stage = "TOKEN"
        // The default app belongs to the target APK. Its component registry cannot see the
        // androidTest-only DebugAppCheckTestHelper registrar, so create the probe app in the
        // instrumentation APK context while retaining the target's Firebase options.
        val probeApp = FirebaseApp.initializeApp(
            InstrumentationRegistry.getInstrumentation().context,
            FirebaseApp.getInstance().options,
            "firebase-access-probe-${UUID.randomUUID()}",
        )
        try {
            debugAppCheck.withDebugProvider<Exception>(probeApp) {
                Tasks.await(FirebaseAppCheck.getInstance(probeApp).getAppCheckToken(false), 30, TimeUnit.SECONDS)
                stage = "GEMINI"
                request(probeApp)
            }
        } catch (error: Exception) {
            throw AssertionError("FIREBASE_ACCESS_PROBE_FAILED_${stage}_${failureCategory(error)}")
        } finally {
            probeApp.delete()
        }
    }

    private fun request(app: FirebaseApp) = runBlocking {
        val model = FirebaseAI.getInstance(app, GenerativeBackend.googleAI()).generativeModel(
            modelName = required("oliviaAiModel"),
            generationConfig = generationConfig { maxOutputTokens = 8; temperature = 0f },
        )
        try {
            val response = withTimeout(30_000) { model.generateContent("Responda somente OK.") }
            if (response.text.isNullOrBlank()) throw AssertionError("EMPTY_MODEL_RESPONSE")
        } catch (error: Exception) { throw error }
    }

    /** Maps provider failures to non-sensitive diagnostic labels for local setup. */
    private fun failureCategory(error: Exception): String {
        if (error is TimeoutCancellationException) return "TIMEOUT"
        val message = error.message.orEmpty()
        return when {
            Regex("(?i)app.?check|attestation|debug secret").containsMatchIn(message) -> "APP_CHECK"
            Regex("(?i)api.?key|application.?id|project.?id").containsMatchIn(message) -> "CONFIG"
            Regex("(?i)model|not.?found").containsMatchIn(message) -> "MODEL"
            Regex("(?i)quota|resource.?exhausted|\\b429\\b").containsMatchIn(message) -> "QUOTA"
            Regex("(?i)network|connect|timeout|unavailable").containsMatchIn(message) -> "NETWORK"
            else -> "PROVIDER"
        }
    }
}
