package com.olivia.companion.audio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.olivia.core.SessionSignal
import com.olivia.core.SystemOliviaClock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the real Android AudioTrack boundary on the device/emulator. It proves that Olivia
 * can begin a local session signal without storing media. It does not claim Bluetooth routing or
 * audibility on Ray-Ban Meta; those require the hardware evidence run.
 */
@RunWith(AndroidJUnit4::class)
class AndroidAudioFeedbackInstrumentationTest {
    @Test
    fun signalModeStartsAnEphemeralLocalTone() = runBlocking {
        val feedback = AndroidAudioFeedback(
            context = InstrumentationRegistry.getInstrumentation().targetContext,
            clock = SystemOliviaClock(),
            initialMode = com.olivia.core.AudioMode.SIGNAL,
        )

        try {
            val result = feedback.signal(SessionSignal.SESSION_STARTED)
            assertTrue("O tom local deve iniciar no AudioTrack Android", result.isSuccess)
        } finally {
            feedback.close()
        }
    }
}
