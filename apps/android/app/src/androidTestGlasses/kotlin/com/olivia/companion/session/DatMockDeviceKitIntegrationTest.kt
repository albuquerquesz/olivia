package com.olivia.companion.session

import android.Manifest
import android.app.Instrumentation
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.olivia.core.AudioFeedback
import com.olivia.core.AudioMode
import com.olivia.core.AudioReceipt
import com.olivia.core.CaptureId
import com.olivia.core.ClockReading
import com.olivia.core.FrameFormat
import com.olivia.core.FrameOrigin
import com.olivia.core.GlassesConnection
import com.olivia.core.InMemoryTimeline
import com.olivia.core.SessionCoordinator
import com.olivia.core.SessionId
import com.olivia.core.SessionSignal
import com.olivia.core.SessionState
import com.olivia.core.SystemOliviaClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Evidence for the SDK mock only. It never asserts Bluetooth hardware behavior, persists a frame,
 * or sends the phone-camera image to Firebase.
 */
@RunWith(AndroidJUnit4::class)
class DatMockDeviceKitIntegrationTest {
    @Test
    fun mockDeviceKitDeliversPortraitNv21FramesAndRecoversSession() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.CAMERA,
            Manifest.permission.READ_MEDIA_VIDEO,
        ).forEach { permission ->
            grant(instrumentation, instrumentation.targetContext.packageName, permission)
            grant(instrumentation, instrumentation.context.packageName, permission)
        }
        val videoUri = InstrumentationRegistry.getArguments().getString(MOCK_VIDEO_URI_ARGUMENT)
            ?.takeIf { it.isNotBlank() }
            ?.let(Uri::parse)
        val clock = SystemOliviaClock()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        // Android 16 terminates a foreground service started from an instrumentation process
        // before its lifecycle callback. Production keeps it enabled; this test isolates DAT.
        val gateway = DatGlassesGateway(
            context,
            clock,
            scope,
            keepAliveInForeground = false,
            forceMockDeviceKit = true,
            mockVideoUri = videoUri,
        )
        val timeline = InMemoryTimeline()
        val coordinator = SessionCoordinator(clock, gateway, SilentAudio(clock), timeline)
        val heartbeatObserver = launch {
            gateway.heartbeat.filterNotNull().collect { coordinator.onCaptureHeartbeat(it) }
        }
        val connectionObserver = launch {
            gateway.connection.collect { coordinator.onConnectionChanged(it) }
        }
        try {
            val capture = CaptureId(SessionId("mock-device-kit"), 1)
            val firstFrame = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(FRAME_TIMEOUT_MS) { gateway.frames.first() }
            }
            coordinator.start(capture.sessionId).getOrElse { failure ->
                throw AssertionError("DAT_START_FAILED: ${gateway.lastStartError?.message}", failure)
            }
            val frame = firstFrame.await()

            // MockDeviceKit's phone-camera feed can be 480x640 even when HIGH is requested; the
            // contract we need here is portrait orientation and tightly packed NV21. Physical
            // HIGH glasses frames are 720x1280 and are covered by the hardware gate.
            assertEquals(FrameOrigin.DAT, frame.origin)
            assertEquals(FrameFormat.NV21, frame.format)
            assertTrue("expected portrait frame", frame.height >= frame.width)
            assertTrue(
                "unexpected orientation ratio ${frame.width}x${frame.height}",
                frame.height.toFloat() / frame.width >= MIN_PORTRAIT_RATIO,
            )
            assertEquals(frame.width * frame.height * 3 / 2, frame.bytes.size)
            awaitState(coordinator, SessionState.STARTED)

            // A stopped stream with a live session is a capture outage, not a pause request.
            gateway.pauseCapture()
            delay(4_100)
            coordinator.checkCaptureHealth()
            awaitState(coordinator, SessionState.DEGRADED)
            gateway.resumeCapture(capture)
            awaitState(coordinator, SessionState.STARTED)

            coordinator.pause().getOrThrow()
            awaitState(coordinator, SessionState.PAUSED)
            coordinator.resume().getOrThrow()
            awaitState(coordinator, SessionState.STARTED)
            coordinator.stop().getOrThrow()
            awaitState(coordinator, SessionState.STOPPED)
        } finally {
            heartbeatObserver.cancel()
            connectionObserver.cancel()
            gateway.close()
            scope.cancel()
        }
    }

    private suspend fun awaitState(coordinator: SessionCoordinator, state: SessionState) {
        withTimeout(STATE_TIMEOUT_MS) { coordinator.snapshot.filter { it?.state == state }.first() }
    }

    private fun grant(instrumentation: Instrumentation, packageName: String, permission: String) {
        instrumentation.uiAutomation.executeShellCommand(
            "pm grant $packageName $permission",
        ).close()
    }

    private class SilentAudio(private val clock: SystemOliviaClock) : AudioFeedback {
        override val mode: AudioMode = AudioMode.SIGNAL
        override suspend fun announce(correction: com.olivia.core.Correction): Result<AudioReceipt> =
            Result.success(AudioReceipt(correction.id, correction.capture, clock.now()))
        override suspend fun signal(signal: SessionSignal): Result<ClockReading> = Result.success(clock.now())
        override suspend fun stop() = Unit
    }

    private companion object {
        const val MOCK_VIDEO_URI_ARGUMENT = "datMockVideoUri"
        const val FRAME_TIMEOUT_MS = 15_000L
        const val STATE_TIMEOUT_MS = 8_000L
        const val MIN_PORTRAIT_RATIO = 1.2f
    }
}
