package com.olivia.core

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** A 2D transform expressed in normalized frame coordinates. */
public data class MotionTransform(
    val translationX: Float = 0f,
    val translationY: Float = 0f,
    val scale: Float = 1f,
    val rotationRadians: Float = 0f,
) {
    init {
        require(translationX.isFinite() && translationY.isFinite())
        require(scale.isFinite() && scale > 0f)
        require(rotationRadians.isFinite())
    }

    /** Applies this camera motion to a normalized ROI around the frame center. */
    public fun apply(roi: NormalizedRoi): NormalizedRoi? {
        val corners = arrayOf(
            transform(roi.x, roi.y),
            transform(roi.x + roi.width, roi.y),
            transform(roi.x, roi.y + roi.height),
            transform(roi.x + roi.width, roi.y + roi.height),
        )
        val left = corners.minOf { it.first }.coerceIn(0f, 1f)
        val top = corners.minOf { it.second }.coerceIn(0f, 1f)
        val right = corners.maxOf { it.first }.coerceIn(0f, 1f)
        val bottom = corners.maxOf { it.second }.coerceIn(0f, 1f)
        if (right - left <= MIN_ROI_EDGE || bottom - top <= MIN_ROI_EDGE) return null
        return NormalizedRoi(left, top, right - left, bottom - top)
    }

    /** Composition where [this] runs after [earlier]. */
    public fun after(earlier: MotionTransform): MotionTransform {
        val angle = rotationRadians
        val rotatedX = scale * (cos(angle) * earlier.translationX - sin(angle) * earlier.translationY)
        val rotatedY = scale * (sin(angle) * earlier.translationX + cos(angle) * earlier.translationY)
        return MotionTransform(
            translationX = translationX + rotatedX,
            translationY = translationY + rotatedY,
            scale = scale * earlier.scale,
            rotationRadians = rotationRadians + earlier.rotationRadians,
        )
    }

    private fun transform(x: Float, y: Float): Pair<Float, Float> {
        val centeredX = x - FRAME_CENTER
        val centeredY = y - FRAME_CENTER
        val rotatedX = scale * (cos(rotationRadians) * centeredX - sin(rotationRadians) * centeredY)
        val rotatedY = scale * (sin(rotationRadians) * centeredX + cos(rotationRadians) * centeredY)
        return FRAME_CENTER + rotatedX + translationX to FRAME_CENTER + rotatedY + translationY
    }

    public companion object {
        public val IDENTITY: MotionTransform = MotionTransform()
        private const val FRAME_CENTER = 0.5f
        private const val MIN_ROI_EDGE = 0.01f
    }
}

/** Quality data returned by a sparse optical-flow implementation. */
public data class MotionEstimate(
    val transform: MotionTransform,
    val featureCount: Int,
    val inlierCount: Int,
    val forwardBackwardErrorPx: Float,
) {
    init {
        require(featureCount >= 0)
        require(inlierCount in 0..featureCount)
        require(forwardBackwardErrorPx.isFinite() && forwardBackwardErrorPx >= 0f)
    }

    public val inlierRatio: Float
        get() = if (featureCount == 0) 0f else inlierCount.toFloat() / featureCount
}

/** Platform adapter for sparse optical flow; :core remains Android/OpenCV free. */
public fun interface SparseMotionEstimator {
    public fun estimate(previous: LumaFrame, current: LumaFrame): MotionEstimate?
}

/** Bounded transform history used to compensate delayed Gemini responses. */
public class MotionHistoryBuffer(
    private val capacity: Int = 32,
) {
    private val entries = ArrayDeque<MotionHistoryEntry>()

    init { require(capacity > 0) }

    public val size: Int get() = entries.size

    public fun append(sequence: Long, atMonotonicMs: Long, transform: MotionTransform, fromSequence: Long = sequence - 1) {
        require(sequence >= 0)
        require(atMonotonicMs >= 0)
        val last = entries.lastOrNull()
        require(last == null || sequence > last.sequence) { "Motion history must be monotonic" }
        entries += MotionHistoryEntry(sequence, atMonotonicMs, transform, fromSequence)
        while (entries.size > capacity) entries.removeFirst()
    }

    /** Returns motion after [sequence], or null when the bounded history no longer covers it. */
    public fun accumulatedSince(sequence: Long): MotionTransform? {
        require(sequence >= 0)
        val first = entries.firstOrNull() ?: return MotionTransform.IDENTITY
        if (sequence < first.fromSequence) return null
        if (entries.any { sequence > it.fromSequence && sequence < it.sequence }) return null
        return entries.filter { it.sequence > sequence }
            .fold(MotionTransform.IDENTITY) { accumulated, entry -> entry.transform.after(accumulated) }
    }

    public fun clear() { entries.clear() }

    private data class MotionHistoryEntry(
        val sequence: Long,
        val atMonotonicMs: Long,
        val transform: MotionTransform,
        val fromSequence: Long,
    )
}

public enum class RoiTrackerState {
    UNINITIALIZED,
    TRACKING,
    NEEDS_REACQUISITION,
}

/** Explicit confidence components used to gate speech from the fast path. */
public data class TrackerConfidence(
    val inlierRatio: Float,
    val forwardBackwardErrorPx: Float,
    val geminiAgeMs: Long?,
    val score: Float,
) {
    init {
        require(inlierRatio in 0f..1f)
        require(forwardBackwardErrorPx >= 0f && forwardBackwardErrorPx.isFinite())
        require(geminiAgeMs == null || geminiAgeMs >= 0)
        require(score in 0f..1f)
    }
}

public data class RoiTrackerPolicy(
    val minimumInlierRatio: Float = 0.55f,
    val maximumForwardBackwardErrorPx: Float = 3.5f,
    val minimumFeatureCount: Int = 8,
    val minimumScore: Float = 0.60f,
    val maximumGeminiAgeMs: Long = 10_000,
    val maximumHeldMotionFrames: Int = 3,
    val reconcileHighIou: Float = 0.55f,
    val reconcileBlendWeight: Float = 0.25f,
) {
    init {
        require(minimumInlierRatio in 0f..1f)
        require(maximumForwardBackwardErrorPx > 0f)
        require(minimumFeatureCount > 0)
        require(minimumScore in 0f..1f)
        require(maximumGeminiAgeMs > 0)
        require(maximumHeldMotionFrames >= 0)
        require(reconcileHighIou in 0f..1f)
        require(reconcileBlendWeight in 0f..1f)
    }
}

public data class RoiTrackerUpdate(
    val state: RoiTrackerState,
    val roi: NormalizedRoi?,
    val confidence: TrackerConfidence?,
    val canSpeak: Boolean,
    val needsReacquisition: Boolean,
)

public data class RoiReconciliation(
    val roi: NormalizedRoi,
    val propagatedGeminiRoi: NormalizedRoi,
    val trackerRoi: NormalizedRoi?,
    val iou: Float,
    val replacedTracker: Boolean,
)

/**
 * Tracks a Gemini ROI between slow validations. It stores transforms only, never frame bytes.
 * Low-quality flow can be held for a few frames to survive motion blur, but it never authorizes
 * speech until confidence is restored. Delayed Gemini boxes are propagated through the bounded
 * transform history and reconciled by IoU.
 */
public class RoiMotionTracker(
    private val policy: RoiTrackerPolicy = RoiTrackerPolicy(),
    historyCapacity: Int = 256,
) {
    private val history = MotionHistoryBuffer(historyCapacity)
    private var state: RoiTrackerState = RoiTrackerState.UNINITIALIZED
    private var roi: NormalizedRoi? = null
    private var lastGeminiAtMs: Long? = null
    private var lastGeminiConfidence: Confidence? = null
    private var lastGeminiSequence: Long? = null
    private var lastSequence: Long? = null
    private var lastEstimate: MotionEstimate? = null
    private var heldFrames: Int = 0

    public fun seed(localization: WorkAreaLocalization, sequence: Long, atMonotonicMs: Long): Boolean {
        if (!validLocalization(localization)) {
            state = RoiTrackerState.NEEDS_REACQUISITION
            return false
        }
        roi = requireNotNull(localization.roi)
        state = RoiTrackerState.TRACKING
        lastGeminiAtMs = atMonotonicMs
        lastGeminiConfidence = localization.confidence
        lastGeminiSequence = sequence
        lastSequence = sequence
        lastEstimate = null
        heldFrames = 0
        history.clear()
        return true
    }

    public fun onFrame(
        stamp: FrameStamp,
        estimate: MotionEstimate?,
        handInsideRoi: Boolean = false,
    ): RoiTrackerUpdate {
        val currentSequence = stamp.sequence
        if (lastSequence != null && currentSequence <= requireNotNull(lastSequence)) {
            return snapshot(stamp.receivedAt.monotonicMs, handInsideRoi)
        }
        if (lastSequence != null) {
            history.append(
                sequence = currentSequence,
                atMonotonicMs = stamp.receivedAt.monotonicMs,
                transform = estimate?.transform ?: MotionTransform.IDENTITY,
                fromSequence = requireNotNull(lastSequence),
            )
        }
        lastSequence = currentSequence
        if (roi == null) {
            state = RoiTrackerState.NEEDS_REACQUISITION
            return snapshot(stamp.receivedAt.monotonicMs, handInsideRoi)
        }
        val quality = estimate?.let(::quality)
        if (estimate != null && quality != null && quality.score >= policy.minimumScore) {
            roi = estimate.transform.apply(requireNotNull(roi))
            lastEstimate = estimate
            heldFrames = 0
            state = if (roi == null) RoiTrackerState.NEEDS_REACQUISITION else RoiTrackerState.TRACKING
        } else if (lastEstimate != null && heldFrames < policy.maximumHeldMotionFrames) {
            roi = requireNotNull(lastEstimate).transform.apply(requireNotNull(roi))
            heldFrames++
            state = if (roi == null) RoiTrackerState.NEEDS_REACQUISITION else RoiTrackerState.TRACKING
        } else {
            state = RoiTrackerState.NEEDS_REACQUISITION
        }
        return snapshot(stamp.receivedAt.monotonicMs, handInsideRoi, quality)
    }

    /** Incorporates a delayed Gemini result captured at [capturedSequence]. */
    public fun reconcile(
        localization: WorkAreaLocalization,
        capturedSequence: Long,
        nowSequence: Long,
        nowMonotonicMs: Long,
    ): RoiReconciliation? {
        if (!validLocalization(localization)) {
            state = RoiTrackerState.NEEDS_REACQUISITION
            return null
        }
        val remote = requireNotNull(localization.roi)
        if (capturedSequence < nowSequence && history.size == 0) return null
        // Missing history or an out-of-bounds transform cannot make an old ROI current.
        val propagated = history.accumulatedSince(capturedSequence)?.apply(remote) ?: return null
        val current = roi
        val overlap = current?.let { iou(it, propagated) } ?: 0f
        val replace = current == null || overlap < policy.reconcileHighIou
        val corrected = if (replace) propagated else blend(current, propagated, policy.reconcileBlendWeight)
        roi = corrected
        state = RoiTrackerState.TRACKING
        lastGeminiAtMs = nowMonotonicMs
        lastGeminiConfidence = localization.confidence
        lastGeminiSequence = nowSequence
        lastSequence = max(lastSequence ?: nowSequence, nowSequence)
        lastEstimate = null
        heldFrames = 0
        return RoiReconciliation(corrected, propagated, current, overlap, replace)
    }

    public fun reset() {
        state = RoiTrackerState.UNINITIALIZED
        roi = null
        lastGeminiAtMs = null
        lastGeminiConfidence = null
        lastGeminiSequence = null
        lastSequence = null
        lastEstimate = null
        heldFrames = 0
        history.clear()
    }

    public fun currentRoi(): NormalizedRoi? = roi
    public fun currentState(): RoiTrackerState = state
    public fun currentSequence(): Long? = lastSequence

    /** Latest cloud confidence paired with the tracker ROI, for local analysis only. */
    public fun currentLocalization(): WorkAreaLocalization? =
        roi?.let { WorkAreaLocalization(true, lastGeminiConfidence ?: Confidence(0f), it) }

    private fun snapshot(
        nowMs: Long,
        handInsideRoi: Boolean,
        quality: TrackerConfidence? = lastEstimate?.let(::quality),
    ): RoiTrackerUpdate {
        val age = lastGeminiAtMs?.let { (nowMs - it).coerceAtLeast(0) }
        val confidence = quality?.copy(geminiAgeMs = age)
        val stale = age == null || age > policy.maximumGeminiAgeMs
        val handPenalty = if (handInsideRoi) 0.75f else 1f
        val adjusted = confidence?.let { it.copy(score = (it.score * handPenalty).coerceIn(0f, 1f)) }
        val canSpeak = state == RoiTrackerState.TRACKING && !stale &&
            adjusted != null && adjusted.score >= policy.minimumScore && !handInsideRoi
        return RoiTrackerUpdate(state, roi, adjusted, canSpeak, state == RoiTrackerState.NEEDS_REACQUISITION || stale)
    }

    private fun quality(estimate: MotionEstimate): TrackerConfidence {
        val inlier = (estimate.inlierRatio / policy.minimumInlierRatio).coerceIn(0f, 1f)
        val fb = (1f - estimate.forwardBackwardErrorPx / policy.maximumForwardBackwardErrorPx).coerceIn(0f, 1f)
        val features = (estimate.featureCount.toFloat() / (policy.minimumFeatureCount * 2f)).coerceIn(0f, 1f)
        return TrackerConfidence(estimate.inlierRatio, estimate.forwardBackwardErrorPx, null, inlier * fb * features)
    }

    private fun validLocalization(localization: WorkAreaLocalization): Boolean =
        localization.targetPresent && localization.confidence.value >= MIN_GEMINI_CONFIDENCE && localization.roi != null

    private fun blend(a: NormalizedRoi, b: NormalizedRoi, weight: Float): NormalizedRoi = NormalizedRoi(
        x = a.x + (b.x - a.x) * weight,
        y = a.y + (b.y - a.y) * weight,
        width = a.width + (b.width - a.width) * weight,
        height = a.height + (b.height - a.height) * weight,
    )

    private fun iou(a: NormalizedRoi, b: NormalizedRoi): Float {
        val left = max(a.x, b.x)
        val top = max(a.y, b.y)
        val right = min(a.x + a.width, b.x + b.width)
        val bottom = min(a.y + a.height, b.y + b.height)
        val intersection = ((right - left).coerceAtLeast(0f) * (bottom - top).coerceAtLeast(0f))
        val union = a.width * a.height + b.width * b.height - intersection
        return if (union <= 0f) 0f else (intersection / union).coerceIn(0f, 1f)
    }

    private companion object {
        const val MIN_GEMINI_CONFIDENCE = 0.80f
    }
}
