package com.olivia.core

/** A correlation key containing no frame bytes, model text, microphone data, or credentials. */
public data class LoopMeasurementId(val capture: CaptureId, val frameSequence: Long)

/** One completed analysis attempt. All timestamps are monotonic milliseconds. */
public data class LoopMeasurement(
    val id: LoopMeasurementId,
    val frameReceivedAtMs: Long,
    val analysisStartedAtMs: Long,
    val analysisFinishedAtMs: Long,
    val audioRequestedAtMs: Long? = null,
    /** Android callback time only; it is not an acoustic measurement in the glasses. */
    val audioStartedAtMs: Long? = null,
) {
    init {
        require(frameReceivedAtMs >= 0 && analysisStartedAtMs >= frameReceivedAtMs)
        require(analysisFinishedAtMs >= analysisStartedAtMs)
        require(audioRequestedAtMs == null || audioRequestedAtMs >= analysisFinishedAtMs)
        require(audioStartedAtMs == null || (audioRequestedAtMs != null && audioStartedAtMs >= audioRequestedAtMs))
    }

    public val inferenceMs: Long get() = analysisFinishedAtMs - analysisStartedAtMs
    public val frameToAudioCallbackMs: Long? get() = audioStartedAtMs?.minus(frameReceivedAtMs)
}

public data class LiveLoopMetricSnapshot(
    val receivedFrames: Long,
    val selectedFrames: Long,
    val discardedFrames: Long,
    val analysisAttempts: Long,
    val audioCallbacks: Long,
    val p50FrameToAudioCallbackMs: Long?,
    val p95FrameToAudioCallbackMs: Long?,
)

/**
 * Bounded in-memory instrumentation for the live loop. It is deliberately not analytics and
 * must be drained/exported only as an aggregate, authorized measurement report.
 */
public class LiveLoopMetrics(private val sampleLimit: Int = 200) {
    private val samples: ArrayDeque<LoopMeasurement> = ArrayDeque()
    private var receivedFrames: Long = 0
    private var selectedFrames: Long = 0
    private var discardedFrames: Long = 0

    init {
        require(sampleLimit > 0)
    }

    public fun onFrameReceived() { receivedFrames++ }

    public fun onFrameDiscarded() { discardedFrames++ }

    public fun finish(measurement: LoopMeasurement) {
        selectedFrames++
        if (samples.size == sampleLimit) samples.removeFirst()
        samples.addLast(measurement)
    }

    public fun snapshot(): LiveLoopMetricSnapshot {
        val frameToAudio = samples.mapNotNull(LoopMeasurement::frameToAudioCallbackMs).sorted()
        return LiveLoopMetricSnapshot(
            receivedFrames = receivedFrames,
            selectedFrames = selectedFrames,
            discardedFrames = discardedFrames,
            analysisAttempts = samples.size.toLong(),
            audioCallbacks = frameToAudio.size.toLong(),
            p50FrameToAudioCallbackMs = percentile(frameToAudio, 0.50),
            p95FrameToAudioCallbackMs = percentile(frameToAudio, 0.95),
        )
    }

    /** Nearest-rank percentile, intentionally null when no audio callback exists. */
    private fun percentile(sortedValues: List<Long>, percentile: Double): Long? {
        if (sortedValues.isEmpty()) return null
        val index = kotlin.math.ceil(percentile * sortedValues.size).toInt().coerceAtLeast(1) - 1
        return sortedValues[index]
    }
}
