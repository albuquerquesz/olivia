package com.olivia.companion.ai

import com.olivia.core.LumaFrame
import com.olivia.core.MotionEstimate
import com.olivia.core.MotionTransform
import com.olivia.core.SparseMotionEstimator
import org.opencv.android.OpenCVLoader
import org.opencv.calib3d.Calib3d
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.MatOfFloat
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.core.TermCriteria
import org.opencv.video.Video
import org.opencv.imgproc.Imgproc
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Android/OpenCV implementation of the platform-neutral sparse motion contract. It downsamples
 * to a bounded grayscale image, uses LK in both directions and RANSAC affine estimation, and
 * returns aggregate quality only. No pixel or point data leaves this class.
 */
class OpenCvSparseMotionEstimator(
    private val maxEdgePx: Int = 320,
    private val maxCorners: Int = 96,
) : SparseMotionEstimator {
    init {
        require(maxEdgePx in 64..1_024)
        require(maxCorners >= 8)
    }

    override fun estimate(previous: LumaFrame, current: LumaFrame): MotionEstimate? {
        if (!ensureLoaded() || previous.width != current.width || previous.height != current.height) return null
        val previousMat = toMat(previous)
        val currentMat = toMat(current)
        val previousSmall = Mat()
        val currentSmall = Mat()
        val corners = MatOfPoint()
        val prevPoints = MatOfPoint2f()
        val nextPoints = MatOfPoint2f()
        val reversePoints = MatOfPoint2f()
        val statusForward = MatOfByte()
        val statusReverse = MatOfByte()
        val errors = MatOfFloat()
        val reverseErrors = MatOfFloat()
        val inlierMask = Mat()
        val affine = Mat()
        return try {
            resize(previousMat, previousSmall)
            resize(currentMat, currentSmall)
            Imgproc.goodFeaturesToTrack(previousSmall, corners, maxCorners, 0.01, 8.0)
            val detected = corners.toArray()
            if (detected.size < MIN_FEATURES) return null
            prevPoints.fromArray(*detected)
            Video.calcOpticalFlowPyrLK(
                previousSmall, currentSmall, prevPoints, nextPoints, statusForward, errors,
                Size(21.0, 21.0), 3, TermCriteria(TermCriteria.COUNT or TermCriteria.EPS, 20, 0.03),
            )
            Video.calcOpticalFlowPyrLK(
                currentSmall, previousSmall, nextPoints, reversePoints, statusReverse, reverseErrors,
                Size(21.0, 21.0), 3, TermCriteria(TermCriteria.COUNT or TermCriteria.EPS, 20, 0.03),
            )
            val forwardStatus = statusForward.toArray()
            val reverseStatus = statusReverse.toArray()
            val forward = nextPoints.toArray()
            val reverse = reversePoints.toArray()
            val source = ArrayList<Point>(detected.size)
            val destination = ArrayList<Point>(detected.size)
            var fbError = 0.0
            var valid = 0
            detected.indices.forEach { index ->
                if (forwardStatus.getOrNull(index)?.toInt() == 1 && reverseStatus.getOrNull(index)?.toInt() == 1) {
                    val f = forward[index]
                    val r = reverse[index]
                    val error = hypot(detected[index].x - r.x, detected[index].y - r.y)
                    if (error <= MAX_FB_ERROR_PX) {
                        source += detected[index]
                        destination += f
                        fbError += error
                        valid++
                    }
                }
            }
            if (valid < MIN_FEATURES) return null
            val sourceMat = MatOfPoint2f(*source.toTypedArray())
            val destinationMat = MatOfPoint2f(*destination.toTypedArray())
            try {
                val estimated = Calib3d.estimateAffinePartial2D(
                    sourceMat, destinationMat, inlierMask, Calib3d.RANSAC, RANSAC_REPROJECTION_PX,
                )
                if (estimated.empty() || estimated.rows() < 2 || estimated.cols() < 3) return null
                estimated.copyTo(affine)
                val a = affine.get(0, 0)[0]
                val b = affine.get(0, 1)[0]
                val tx = affine.get(0, 2)[0]
                val ty = affine.get(1, 2)[0]
                val scale = hypot(a, b)
                if (!scale.isFinite() || scale <= 0.01) return null
                val mask = inlierMask.toArrayInt()
                val inliers = mask.count { it != 0 }
                val edge = maxOf(previousSmall.cols(), previousSmall.rows()).toFloat()
                val sourceEdge = maxOf(previous.width, previous.height).toFloat()
                MotionEstimate(
                    transform = MotionTransform(
                        translationX = (tx / previousSmall.cols()).toFloat(),
                        translationY = (ty / previousSmall.rows()).toFloat(),
                        scale = scale.toFloat(),
                        rotationRadians = atan2(b, a).toFloat(),
                    ),
                    featureCount = valid,
                    inlierCount = inliers.coerceIn(0, valid),
                    forwardBackwardErrorPx = (fbError / valid * sourceEdge / edge).toFloat().coerceAtLeast(0f),
                )
            } finally {
                sourceMat.release()
                destinationMat.release()
            }
        } catch (_: Throwable) {
            null
        } finally {
            previousMat.release()
            currentMat.release()
            previousSmall.release()
            currentSmall.release()
            corners.release()
            prevPoints.release()
            nextPoints.release()
            reversePoints.release()
            statusForward.release()
            statusReverse.release()
            errors.release()
            reverseErrors.release()
            inlierMask.release()
            affine.release()
        }
    }

    private fun toMat(frame: LumaFrame): Mat = Mat(frame.height, frame.width, CvType.CV_8UC1).also {
        it.put(0, 0, frame.pixels)
    }

    private fun resize(source: Mat, destination: Mat) {
        val edge = maxOf(source.cols(), source.rows())
        if (edge <= maxEdgePx) source.copyTo(destination)
        else {
            val scale = maxEdgePx.toDouble() / edge
            Imgproc.resize(source, destination, Size(source.cols() * scale, source.rows() * scale))
        }
    }

    private fun Mat.toArrayInt(): IntArray {
        val bytes = ByteArray((rows() * cols()).coerceAtLeast(0))
        if (bytes.isNotEmpty()) get(0, 0, bytes)
        return IntArray(bytes.size) { bytes[it].toInt() and 0xff }
    }

    private fun ensureLoaded(): Boolean = synchronized(OpenCvSparseMotionEstimator::class.java) {
        if (loaded) return@synchronized true
        loaded = runCatching { OpenCVLoader.initLocal() }.getOrDefault(false)
        loaded
    }

    private companion object {
        var loaded: Boolean = false
        const val MIN_FEATURES = 8
        const val MAX_FB_ERROR_PX = 3.5
        const val RANSAC_REPROJECTION_PX = 3.0
    }
}
