package com.olivia.companion.ai

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.debug.testing.DebugAppCheckTestHelper
import com.olivia.core.AnalyzerSource
import com.olivia.core.AnalysisFailure
import com.olivia.core.CaptureId
import com.olivia.core.DeterministicLocalVisualAnalyzer
import com.olivia.core.Frame
import com.olivia.core.FrameFormat
import com.olivia.core.FrameOrigin
import com.olivia.core.FrameStamp
import com.olivia.core.GeminiFrameAnalyzer
import com.olivia.core.SessionId
import com.olivia.core.SystemOliviaClock
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Live Firebase gate for the exact ROI transport used by the glasses APK. */
@RunWith(AndroidJUnit4::class)
class FirebaseRoiIntegrationTest {
    private val arguments = InstrumentationRegistry.getArguments()
    private val debugAppCheck = DebugAppCheckTestHelper.fromInstrumentationArgs()

    @Test
    fun liveTransportReturnsOnlyRoiThatBecomesLocalAnalysis() {
        requireArgument("oliviaLiveAiEnabled", "true")
        val model = requireArgument("oliviaAiModel")
        requireArgument("firebaseAppCheckDebugSecret")
        val probeApp = FirebaseApp.initializeApp(
            InstrumentationRegistry.getInstrumentation().context,
            FirebaseApp.getInstance().options,
            "firebase-roi-probe-${UUID.randomUUID()}",
        )
        try {
            debugAppCheck.withDebugProvider<Exception>(probeApp) {
                runBlocking {
                    val clock = SystemOliviaClock()
                    val analyzer = GeminiFrameAnalyzer(
                        clock = clock,
                        transport = FirebaseGeminiVisualTransport.forFirebaseApp(probeApp, model),
                        localVisualAnalyzer = DeterministicLocalVisualAnalyzer(),
                        lumaDecoder = AndroidFrameLumaDecoder(),
                        // This is an infrastructure probe: it must tolerate a cold Firebase
                        // connection. The production analyzer keeps its 2 s live-loop timeout.
                        timeoutMs = 30_000,
                    )
                    val result = analyzer.analyze(syntheticFrame(clock))
                    assertTrue(
                        "FIREBASE_ROI_PIPELINE_FAILED_${failureCode(result)}",
                        result.isSuccess,
                    )
                    val analysis = result.getOrThrow()
                    assertEquals(AnalyzerSource.GEMINI_ROI_LOCAL, analysis.source)
                    assertTrue(
                        "LOCAL_DIAGNOSIS_OUT_OF_SCOPE",
                        analysis.diagnoses.all { it.kind.name in setOf("FRAMING", "LIGHT", "HORIZON") },
                    )
                }
            }
        } finally {
            probeApp.delete()
        }
    }

    private fun requireArgument(key: String, expected: String? = null): String {
        val value = arguments.getString(key)?.takeIf { it.isNotBlank() }
            ?: throw AssertionError("LIVE_CONFIG_MISSING")
        if (expected != null && value != expected) throw AssertionError("LIVE_CONFIG_INVALID")
        return value
    }

    /** Test-only, non-sensitive diagnostic. It deliberately omits provider response and image data. */
    private fun failureCode(result: Result<*>): String =
        (result.exceptionOrNull() as? AnalysisFailure)?.code?.name ?: "UNKNOWN"

    private fun syntheticFrame(clock: SystemOliviaClock): Frame {
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(0xffeeeeee.toInt())
            for (y in 160 until 400) for (x in 280 until 460) bitmap.setPixel(x, y, 0xff1565c0.toInt())
            val jpeg = ByteArrayOutputStream().use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream))
                stream.toByteArray()
            }
            return Frame(
                FrameStamp(CaptureId(SessionId("live-roi-probe"), 1), 0, clock.now()),
                512,
                512,
                FrameFormat.JPEG,
                FrameOrigin.MOCK,
                jpeg,
            )
        } finally {
            bitmap.recycle()
        }
    }
}
