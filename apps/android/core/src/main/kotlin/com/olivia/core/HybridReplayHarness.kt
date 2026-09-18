package com.olivia.core

/** One authorized, in-memory replay sample. Pixel buffers are owned by the caller and never logged. */
public data class ReplayFrame(
    val stamp: FrameStamp,
    val luma: LumaFrame,
    val referenceLocalization: WorkAreaLocalization,
    val referenceDiagnoses: List<VisualDiagnosis>,
)

public data class HybridReplayReport(
    val frames: Int,
    val comparableFrames: Int,
    val agreementRate: Float,
    val falseWarningRate: Float,
    val oscillations: Int,
    val trackerLosses: Int,
    val reacquisitionFrames: Int?,
    val meanRoiIou: Float?,
)

/**
 * Deterministic harness for comparing the fast path with a Gemini-every-frame reference. A real
 * capture can be fed only with explicit authorization; this class stores aggregate counters only.
 */
public class HybridReplayHarness(
    private val analyzer: HybridVisualAnalyzer,
    private val frameFactory: (ReplayFrame) -> Frame,
    private val validationEveryFrames: Int = 10,
) {
    init { require(validationEveryFrames > 0) }

    public fun run(frames: List<ReplayFrame>): HybridReplayReport {
        if (frames.isEmpty()) return HybridReplayReport(0, 0, 0f, 0f, 0, 0, null, null)
        analyzer.reset()
        var comparable = 0
        var agreement = 0
        var falseWarnings = 0
        var oscillations = 0
        var trackerLosses = 0
        var reacquiredAt: Int? = null
        var lossStart: Int? = null
        var lastDirection: Direction? = null
        var iouSum = 0f
        var iouCount = 0
        frames.forEachIndexed { index, replay ->
            if (index % validationEveryFrames == 0) {
                analyzer.onRemoteLocalization(replay.stamp, replay.referenceLocalization)
            }
            val result = analyzer.processFast(frameFactory(replay))?.getOrNull()
            val reference = actionable(replay.referenceDiagnoses)
            val actual = result?.let { actionable(it.diagnoses) }
            if (actual == null) {
                trackerLosses++
                if (lossStart == null) lossStart = index
            } else {
                if (reacquiredAt == null && lossStart != null) reacquiredAt = index - requireNotNull(lossStart)
                comparable++
                if (sameAction(reference, actual)) agreement++
                if (reference == null) falseWarnings++
                val direction = actual.direction
                if (direction != null && lastDirection != null && direction != lastDirection) oscillations++
                if (direction != null) lastDirection = direction
            }
            analyzer.currentTrackedRoi()?.let { tracked ->
                val expected = replay.referenceLocalization.roi
                if (expected != null) {
                    iouSum += iou(tracked, expected)
                    iouCount++
                }
            }
        }
        return HybridReplayReport(
            frames = frames.size,
            comparableFrames = comparable,
            agreementRate = if (comparable == 0) 0f else agreement.toFloat() / comparable,
            falseWarningRate = if (comparable == 0) 0f else falseWarnings.toFloat() / comparable,
            oscillations = oscillations,
            trackerLosses = trackerLosses,
            reacquisitionFrames = reacquiredAt,
            meanRoiIou = if (iouCount == 0) null else iouSum / iouCount,
        )
    }

    private fun actionable(diagnoses: List<VisualDiagnosis>): VisualDiagnosis? = diagnoses
        .filter { it.severity != Severity.OK && it.direction != null }
        .maxByOrNull { it.severity.ordinal }

    private fun sameAction(a: VisualDiagnosis?, b: VisualDiagnosis?): Boolean =
        (a == null && b == null) || (a?.kind == b?.kind && a?.direction == b?.direction && a?.severity == b?.severity)

    private fun iou(a: NormalizedRoi, b: NormalizedRoi): Float {
        val left = maxOf(a.x, b.x)
        val top = maxOf(a.y, b.y)
        val right = minOf(a.x + a.width, b.x + b.width)
        val bottom = minOf(a.y + a.height, b.y + b.height)
        val intersection = (right - left).coerceAtLeast(0f) * (bottom - top).coerceAtLeast(0f)
        val union = a.width * a.height + b.width * b.height - intersection
        return if (union <= 0f) 0f else intersection / union
    }
}
