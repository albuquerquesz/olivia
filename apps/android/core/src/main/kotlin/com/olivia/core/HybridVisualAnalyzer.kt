package com.olivia.core

/** Optional per-frame fast path implemented by the Android/OpenCV adapter. */
public interface FastFrameProcessor {
    /** Returns null while the tracker is unseeded or not confident enough to speak. */
    public fun processFast(frame: Frame): Result<FrameAnalysis>?
}

/**
 * Local tracker between slow Gemini validations. It intentionally keeps the network analyzer as
 * the source of truth for reacquisition, while every accepted camera frame can be classified
 * locally in a few milliseconds. Only a bounded luma reference and transform history are held.
 */
public class HybridVisualAnalyzer(
    private val remote: FrameAnalyzer,
    private val clock: OliviaClock,
    private val localVisualAnalyzer: LocalVisualAnalyzer,
    private val lumaDecoder: FrameLumaDecoder,
    private val motionEstimator: SparseMotionEstimator,
    private val tracker: RoiMotionTracker = RoiMotionTracker(),
    private val handInsideRoi: (LumaFrame, NormalizedRoi) -> Boolean = { _, _ -> false },
) : FrameAnalyzer, FastFrameProcessor {
    private val lock = Any()
    private var previousLuma: LumaFrame? = null
    private var latestSequence: Long? = null
    private val framingHysteresis = FramingHysteresis()

    override suspend fun analyze(frame: Frame): Result<FrameAnalysis> = remote.analyze(frame)

    override fun processFast(frame: Frame): Result<FrameAnalysis>? = synchronized(lock) {
        latestSequence = maxOf(latestSequence ?: frame.stamp.sequence, frame.stamp.sequence)
        val decoded = lumaDecoder.decode(frame) ?: run {
            previousLuma = null
            return@synchronized null
        }
        // Keep the fast path bounded at the same ~320 px budget used by the motion estimator.
        // Gemini still receives the original frame through analyze(); only ephemeral local luma
        // is reduced here.
        val luma = decoded.downsampled(FAST_PATH_MAX_EDGE)
        val previous = previousLuma
        previousLuma = luma
        if (previous == null || tracker.currentState() == RoiTrackerState.UNINITIALIZED) return@synchronized null
        val estimate = runCatching { motionEstimator.estimate(previous, luma) }.getOrNull()
        val roiBeforeFrame = tracker.currentRoi()
        val hand = roiBeforeFrame?.let { runCatching { handInsideRoi(luma, it) }.getOrDefault(false) } ?: false
        val update = tracker.onFrame(frame.stamp, estimate, handInsideRoi = hand)
        if (!update.canSpeak) return@synchronized null
        val localization = tracker.currentLocalization() ?: return@synchronized null
        val diagnoses = localVisualAnalyzer.analyze(luma, localization)
            .map { framingHysteresis.filter(it, localization.roi) }
            .let { filtered -> suppressMotionCorrections(filtered, estimate, localization.roi) }
        val analyzedAt = clock.now()
        Result.success(
            FrameAnalysis(
                frameStamp = frame.stamp,
                analyzedAt = if (analyzedAt.monotonicMs < frame.stamp.receivedAt.monotonicMs) frame.stamp.receivedAt else analyzedAt,
                latencyMs = (analyzedAt.monotonicMs - frame.stamp.receivedAt.monotonicMs).coerceAtLeast(0),
                source = AnalyzerSource.TRACKER_LOCAL,
                diagnoses = diagnoses,
            ),
        )
    }

    /** Called by the remote analyzer after schema validation; never receives raw bytes. */
    public fun onRemoteLocalization(frameStamp: FrameStamp, localization: WorkAreaLocalization) {
        synchronized(lock) {
            val current = latestSequence ?: frameStamp.sequence
            tracker.reconcile(
                localization = localization,
                capturedSequence = frameStamp.sequence,
                nowSequence = maxOf(current, frameStamp.sequence),
                nowMonotonicMs = clock.now().monotonicMs,
            )
        }
    }

    public fun reset(): Unit = synchronized(lock) {
        previousLuma = null
        latestSequence = null
        tracker.reset()
        framingHysteresis.reset()
    }

    public fun currentTrackedRoi(): NormalizedRoi? = synchronized(lock) { tracker.currentRoi() }

    private fun suppressMotionCorrections(
        diagnoses: List<VisualDiagnosis>,
        estimate: MotionEstimate?,
        roi: NormalizedRoi?,
    ): List<VisualDiagnosis> {
        val motion = estimate?.transform ?: return diagnoses
        val target = roi ?: return diagnoses
        return diagnoses.map { diagnosis ->
            val towardCenter = when (diagnosis.direction) {
                Direction.TURN_RIGHT -> target.centerX > 0.5f && motion.translationX < -MIN_MOTION
                Direction.TURN_LEFT -> target.centerX < 0.5f && motion.translationX > MIN_MOTION
                Direction.RAISE -> target.centerY < 0.5f && motion.translationY > MIN_MOTION
                Direction.LOWER -> target.centerY > 0.5f && motion.translationY < -MIN_MOTION
                else -> false
            }
            if (towardCenter) VisualDiagnosis(diagnosis.kind, Severity.OK, diagnosis.confidence, null) else diagnosis
        }
    }

    private companion object {
        const val FAST_PATH_MAX_EDGE = 320
        const val MIN_MOTION = 0.003f
    }
}

/** Entry/exit dead-zone hysteresis prevents chatter when an ROI sits on the center boundary. */
public class FramingHysteresis(
    private val enterOffset: Float = 0.12f,
    private val exitOffset: Float = 0.08f,
) {
    private var outside = false
    private var lastDeviation: VisualDiagnosis? = null

    init {
        require(enterOffset > exitOffset && exitOffset >= 0f && enterOffset <= 1f)
    }

    public fun filter(diagnosis: VisualDiagnosis, roi: NormalizedRoi?): VisualDiagnosis {
        if (diagnosis.kind != DiagnosisKind.FRAMING || roi == null) return diagnosis
        val offset = maxOf(kotlin.math.abs(roi.centerX - .5f), kotlin.math.abs(roi.centerY - .5f))
        if (diagnosis.severity != Severity.OK) lastDeviation = diagnosis
        outside = if (outside) offset > exitOffset else offset > enterOffset
        if (outside) return if (diagnosis.severity == Severity.OK) lastDeviation ?: diagnosis else diagnosis
        return VisualDiagnosis(DiagnosisKind.FRAMING, Severity.OK, diagnosis.confidence, null)
    }

    public fun reset() { outside = false; lastDeviation = null }
}
