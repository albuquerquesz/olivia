package com.olivia.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LiveLoopMetricsTest {
    @Test
    fun `counts sampling and calculates callback percentiles from paired attempts`() {
        val metrics = LiveLoopMetrics()
        repeat(5) { metrics.onFrameReceived() }
        metrics.onFrameDiscarded()
        metrics.finish(measurement(sequence = 1, audioStartedAtMs = 1_100))
        metrics.finish(measurement(sequence = 2, audioStartedAtMs = 1_200))
        metrics.finish(measurement(sequence = 3, audioStartedAtMs = 1_400))

        val snapshot = metrics.snapshot()

        assertEquals(5, snapshot.receivedFrames)
        assertEquals(3, snapshot.selectedFrames)
        assertEquals(1, snapshot.discardedFrames)
        assertEquals(200, snapshot.p50FrameToAudioCallbackMs)
        assertEquals(400, snapshot.p95FrameToAudioCallbackMs)
    }

    @Test
    fun `does not turn an audio request into a playback callback`() {
        val metrics = LiveLoopMetrics()
        metrics.finish(measurement(sequence = 1, audioStartedAtMs = null))

        val snapshot = metrics.snapshot()

        assertEquals(0, snapshot.audioCallbacks)
        assertNull(snapshot.p50FrameToAudioCallbackMs)
        assertNull(snapshot.p95FrameToAudioCallbackMs)
    }

    private fun measurement(sequence: Long, audioStartedAtMs: Long?): LoopMeasurement = LoopMeasurement(
        id = LoopMeasurementId(CaptureId(SessionId("metrics"), 1), sequence),
        frameReceivedAtMs = 1_000,
        analysisStartedAtMs = 1_010,
        analysisFinishedAtMs = 1_050,
        audioRequestedAtMs = audioStartedAtMs?.minus(10),
        audioStartedAtMs = audioStartedAtMs,
    )
}
