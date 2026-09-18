package com.olivia.companion.ai

import com.olivia.core.Confidence
import com.olivia.core.LocalRoiProposer
import com.olivia.core.LumaFrame
import com.olivia.core.NormalizedRoi
import com.olivia.core.WorkAreaLocalization
import kotlin.math.sqrt
import org.opencv.android.OpenCVLoader
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.Rect
import org.opencv.imgproc.Imgproc

/**
 * Conservative OpenCV proposal used only before a cloud ROI is available.
 *
 * This is deliberately a saliency heuristic, not semantic tattoo/person recognition: it looks
 * for a compact, detailed region near the expected center of the vertical composition. It may
 * return null. Gemini remains responsible for validation/reacquisition and no image leaves this
 * class or is retained after [propose] returns.
 */
class OpenCvWorkAreaProposer : LocalRoiProposer {
    override fun propose(luma: LumaFrame): WorkAreaLocalization? {
        if (!ensureLoaded()) return null
        val source = Mat(luma.height, luma.width, CvType.CV_8UC1)
        val blurred = Mat()
        val edges = Mat()
        return try {
            source.put(0, 0, luma.pixels)
            Imgproc.GaussianBlur(source, blurred, BLUR_SIZE, 0.0)
            Imgproc.Canny(blurred, edges, CANNY_LOW, CANNY_HIGH)
            val candidates = candidateRects(luma.width, luma.height)
                .map { rect -> score(edges, source, rect) }
                .sortedByDescending(Candidate::score)
            val best = candidates.firstOrNull() ?: return null
            val runnerUp = candidates.getOrNull(1)
            val dominance = (best.score - (runnerUp?.score ?: 0.0)).coerceAtLeast(0.0)
            val confidence = (
                DETAIL_WEIGHT * best.detail +
                    CONTRAST_WEIGHT * best.contrast +
                    DOMINANCE_WEIGHT * (dominance / DOMINANCE_NORMALIZER).coerceIn(0.0, 1.0) +
                    CENTER_WEIGHT * best.centerPrior
                ).coerceIn(0.0, 1.0)
            if (confidence < MINIMUM_CONFIDENCE) return null
            WorkAreaLocalization(
                targetPresent = true,
                confidence = Confidence(confidence.toFloat()),
                roi = NormalizedRoi(
                    x = best.rect.x.toFloat() / luma.width,
                    y = best.rect.y.toFloat() / luma.height,
                    width = best.rect.width.toFloat() / luma.width,
                    height = best.rect.height.toFloat() / luma.height,
                ),
            )
        } catch (_: Throwable) {
            null
        } finally {
            source.release()
            blurred.release()
            edges.release()
        }
    }

    private fun candidateRects(width: Int, height: Int): List<Rect> {
        val roiWidth = (width * ROI_WIDTH).toInt().coerceIn(1, width)
        val roiHeight = (height * ROI_HEIGHT).toInt().coerceIn(1, height)
        return GRID_POSITIONS.flatMap { y ->
            GRID_POSITIONS.map { x ->
                Rect(
                    (x * width - roiWidth / 2).toInt().coerceIn(0, width - roiWidth),
                    (y * height - roiHeight / 2).toInt().coerceIn(0, height - roiHeight),
                    roiWidth,
                    roiHeight,
                )
            }
        }
    }

    private fun score(edges: Mat, source: Mat, rect: Rect): Candidate {
        val edgeWindow = edges.submat(rect)
        val sourceWindow = source.submat(rect)
        return try {
            val detail = (Core.countNonZero(edgeWindow).toDouble() / rect.area()).coerceIn(0.0, 1.0)
            val mean = MatOfDouble()
            val deviation = MatOfDouble()
            try {
                Core.meanStdDev(sourceWindow, mean, deviation)
                val contrast = ((deviation.toArray().firstOrNull() ?: 0.0) / CONTRAST_NORMALIZER)
                    .coerceIn(0.0, 1.0)
                val centerX = (rect.x + rect.width / 2.0) / source.cols()
                val centerY = (rect.y + rect.height / 2.0) / source.rows()
                val distance = sqrt((centerX - .5) * (centerX - .5) + (centerY - .5) * (centerY - .5))
                val centerPrior = (1.0 - distance / MAX_CENTER_DISTANCE).coerceIn(0.0, 1.0)
                Candidate(rect, detail, contrast, centerPrior, detail + contrast)
            } finally {
                mean.release()
                deviation.release()
            }
        } finally {
            edgeWindow.release()
            sourceWindow.release()
        }
    }

    private data class Candidate(
        val rect: Rect,
        val detail: Double,
        val contrast: Double,
        val centerPrior: Double,
        val score: Double,
    )

    private fun ensureLoaded(): Boolean = synchronized(OpenCvWorkAreaProposer::class.java) {
        if (loaded) return@synchronized true
        loaded = runCatching { OpenCVLoader.initLocal() }.getOrDefault(false)
        loaded
    }

    private companion object {
        var loaded: Boolean = false
        val BLUR_SIZE: org.opencv.core.Size = org.opencv.core.Size(3.0, 3.0)
        const val CANNY_LOW = 48.0
        const val CANNY_HIGH = 128.0
        const val ROI_WIDTH = .38f
        const val ROI_HEIGHT = .42f
        val GRID_POSITIONS = listOf(.20f, .35f, .50f, .65f, .80f)
        const val CONTRAST_NORMALIZER = 64.0
        const val DOMINANCE_NORMALIZER = .12
        const val DETAIL_WEIGHT = .35
        const val CONTRAST_WEIGHT = .35
        const val DOMINANCE_WEIGHT = .20
        const val CENTER_WEIGHT = .10
        const val MAX_CENTER_DISTANCE = .70710678118
        const val MINIMUM_CONFIDENCE = .80
    }
}
