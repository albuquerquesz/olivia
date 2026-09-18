package com.olivia.companion.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.olivia.core.LumaFrame
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Native OpenCV smoke: proves the adapter loads and returns aggregate motion on a synthetic pair. */
@RunWith(AndroidJUnit4::class)
class OpenCvSparseMotionEstimatorTest {
    @Test
    fun translatedTextureProducesRansacEstimate() {
        val previous = texture(160, 120, 0)
        val current = texture(160, 120, 4)
        val estimate = OpenCvSparseMotionEstimator(maxEdgePx = 160).estimate(previous, current)
        val result = requireNotNull(estimate)
        assertTrue(result.featureCount >= 8)
        assertTrue(result.inlierCount > 0)
        assertTrue(result.forwardBackwardErrorPx < 3.5f)
    }

    private fun texture(width: Int, height: Int, shift: Int): LumaFrame {
        val pixels = ByteArray(width * height) { 20 }
        for (y in 12 until height - 12 step 12) {
            for (x in 12 until width - 12 step 12) {
                val left = (x + shift).coerceIn(2, width - 3)
                val top = y
                for (dy in -2..2) for (dx in -2..2) {
                    pixels[(top + dy) * width + (left + dx)] = 230.toByte()
                }
            }
        }
        return LumaFrame(width, height, pixels)
    }
}
