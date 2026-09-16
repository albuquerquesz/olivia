package com.olivia.companion.ai

import android.graphics.Bitmap
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.olivia.core.AnalyzerSource
import com.olivia.core.DeterministicLocalVisualAnalyzer
import com.olivia.core.Frame
import com.olivia.core.FrameFormat
import com.olivia.core.FrameLumaDecoder
import com.olivia.core.FrameOrigin
import com.olivia.core.FrameStamp
import com.olivia.core.GeminiVisualTransport
import com.olivia.core.HybridVisualAnalyzer
import com.olivia.core.LumaFrameDecoder
import com.olivia.core.NormalizedRoi
import com.olivia.core.OliviaClock
import com.olivia.core.WorkAreaLocalization
import com.olivia.core.Confidence
import com.olivia.core.CaptureId
import com.olivia.core.SessionId
import com.olivia.core.ClockReading
import java.io.ByteArrayOutputStream
import kotlin.math.ceil
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** On-device fast-loop measurement; aggregate-only and independent of network credentials. */
@RunWith(AndroidJUnit4::class)
class HybridFastPathMeasurementTest {
    @Test
    fun localTrackerP50P95OnDevice() {
        val clock = DeviceClock()
        val analyzer = HybridVisualAnalyzer(
            remote = object : com.olivia.core.FrameAnalyzer {
                override suspend fun analyze(frame: Frame): Result<com.olivia.core.FrameAnalysis> =
                    Result.failure(IllegalStateException("unused"))
            },
            clock = clock,
            localVisualAnalyzer = DeterministicLocalVisualAnalyzer(),
            lumaDecoder = AndroidFrameLumaDecoder(),
            motionEstimator = OpenCvSparseMotionEstimator(),
        )
        val capture = CaptureId(SessionId("fast-measurement"), 1)
        analyzer.onRemoteLocalization(
            FrameStamp(capture, 0, clock.now()),
            WorkAreaLocalization(true, Confidence(.95f), NormalizedRoi(.34f, .30f, .28f, .30f)),
        )
        val samples = mutableListOf<Long>()
        var successful = 0
        (0L until SAMPLE_COUNT).forEach { sequence ->
            val frame = jpegFrame(capture, sequence, clock)
            val started = System.nanoTime()
            val result = analyzer.processFast(frame)
            samples += ((System.nanoTime() - started) / 1_000_000).coerceAtLeast(0)
            if (result?.isSuccess == true && result.getOrThrow().source == AnalyzerSource.TRACKER_LOCAL) successful++
        }
        val sorted = samples.sorted()
        fun percentile(p: Double): Long = sorted[(ceil(sorted.size * p).toInt() - 1).coerceAtLeast(0)]
        Log.i(
            "OliviaHybridFast",
            "samples=${samples.size} successes=$successful p50_ms=${percentile(.5)} p95_ms=${percentile(.95)}",
        )
        assertTrue("FAST_PATH_NO_SUCCESS", successful > 0)
    }

    private fun jpegFrame(capture: CaptureId, sequence: Long, clock: OliviaClock): Frame {
        val width = 640
        val height = 360
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return try {
            bitmap.eraseColor(0xff303030.toInt())
            val shift = (sequence * 2).toInt()
            for (y in 95 until 245 step 10) for (x in 210 until 430 step 10) {
                val left = (x + shift).coerceIn(2, width - 3)
                for (dy in -2..2) for (dx in -2..2) bitmap.setPixel(left + dx, y + dy, 0xffe0e0e0.toInt())
            }
            val bytes = ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 80, output))
                output.toByteArray()
            }
            Frame(FrameStamp(capture, sequence, clock.now()), width, height, FrameFormat.JPEG, FrameOrigin.MOCK, bytes)
        } finally {
            bitmap.recycle()
        }
    }

    private class DeviceClock : OliviaClock {
        private val start = System.nanoTime()
        override fun now(): ClockReading = ClockReading(
            (System.nanoTime() - start) / 1_000_000,
            System.currentTimeMillis(),
        )
    }

    private companion object { const val SAMPLE_COUNT = 12 }
}
