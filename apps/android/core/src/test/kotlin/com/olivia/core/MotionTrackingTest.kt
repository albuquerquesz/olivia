package com.olivia.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MotionTrackingTest {
    @Test
    fun lumaDownsamplePreservesPortraitAspectAndBounds() {
        val source = LumaFrame(720, 1_280, ByteArray(720 * 1_280) { index -> (index % 251).toByte() })
        val reduced = source.downsampled(320)

        assertEquals(180, reduced.width)
        assertEquals(320, reduced.height)
        assertEquals(180 * 320, reduced.pixels.size)
        assertTrue(reduced.pixels.any { it.toInt() != 0 })
    }

    private val capture = CaptureId(SessionId("motion"), 1)

    @Test
    fun `composes translations and keeps ROI inside normalized bounds`() {
        val first = MotionTransform(translationX = .10f)
        val second = MotionTransform(translationX = .05f)
        val total = second.after(first)
        val roi = assertNotNull(total.apply(NormalizedRoi(.30f, .30f, .20f, .20f)))

        assertEquals(.45f, roi.x, .0001f)
        assertEquals(.30f, roi.y, .0001f)
        assertEquals(.20f, roi.width, .0001f)
        assertEquals(.20f, roi.height, .0001f)
    }

    @Test
    fun `bounded history propagates only covered transforms`() {
        val history = MotionHistoryBuffer(capacity = 2)
        history.append(1, 100, MotionTransform.IDENTITY)
        history.append(2, 200, MotionTransform(translationX = .10f))
        history.append(3, 300, MotionTransform(translationX = .05f))

        assertNull(history.accumulatedSince(0))
        assertEquals(.15f, requireNotNull(history.accumulatedSince(1)).translationX, .0001f)
        assertEquals(.05f, requireNotNull(history.accumulatedSince(2)).translationX, .0001f)
    }

    @Test
    fun `confident tracker moves ROI and authorizes speech`() {
        val tracker = RoiMotionTracker()
        assertTrue(tracker.seed(location(.40f), sequence = 1, atMonotonicMs = 100))
        val update = tracker.onFrame(
            stamp(sequence = 2, atMonotonicMs = 200),
            estimate = MotionEstimate(MotionTransform(translationX = .08f), 20, 18, .5f),
        )

        assertEquals(RoiTrackerState.TRACKING, update.state)
        assertEquals(.48f, requireNotNull(update.roi).x, .0001f)
        assertTrue(update.canSpeak)
        assertFalse(update.needsReacquisition)
    }

    @Test
    fun `blur holds last motion briefly but stays silent then requests reacquisition`() {
        val tracker = RoiMotionTracker(RoiTrackerPolicy(maximumHeldMotionFrames = 2))
        tracker.seed(location(.40f), sequence = 1, atMonotonicMs = 100)
        val good = MotionEstimate(MotionTransform(translationX = .04f), 20, 18, .5f)
        tracker.onFrame(stamp(2, 200), good)

        val held = tracker.onFrame(stamp(3, 300), estimate = null)
        assertEquals(RoiTrackerState.TRACKING, held.state)
        assertFalse(held.canSpeak)

        tracker.onFrame(stamp(4, 400), estimate = null)
        val lost = tracker.onFrame(stamp(5, 500), estimate = null)
        assertEquals(RoiTrackerState.NEEDS_REACQUISITION, lost.state)
        assertTrue(lost.needsReacquisition)
        assertFalse(lost.canSpeak)
    }

    @Test
    fun `reconciliation propagates delayed Gemini box and blends matching tracker`() {
        val tracker = RoiMotionTracker()
        tracker.seed(location(.40f), sequence = 1, atMonotonicMs = 100)
        tracker.onFrame(
            stamp(2, 200),
            MotionEstimate(MotionTransform(translationX = .10f), 20, 18, .5f),
        )

        val reconciliation = tracker.reconcile(
            localization = location(.40f),
            capturedSequence = 1,
            nowSequence = 2,
            nowMonotonicMs = 300,
        )

        assertNotNull(reconciliation)
        assertFalse(reconciliation.replacedTracker)
        assertTrue(reconciliation.iou > .9f)
        assertEquals(.50f, reconciliation.propagatedGeminiRoi.x, .0001f)
        assertEquals(.50f, requireNotNull(reconciliation.roi).x, .0001f)
    }

    @Test
    fun `stale Gemini or hand signal suppresses speech`() {
        val tracker = RoiMotionTracker(RoiTrackerPolicy(maximumGeminiAgeMs = 500))
        tracker.seed(location(.40f), sequence = 1, atMonotonicMs = 100)
        val estimate = MotionEstimate(MotionTransform.IDENTITY, 20, 18, .5f)

        val stale = tracker.onFrame(stamp(2, 700), estimate)
        assertTrue(stale.needsReacquisition)
        assertFalse(stale.canSpeak)

        tracker.reconcile(location(.40f), capturedSequence = 2, nowSequence = 2, nowMonotonicMs = 700)
        val hand = tracker.onFrame(stamp(3, 800), estimate, handInsideRoi = true)
        assertFalse(hand.canSpeak)
    }

    private fun location(x: Float): WorkAreaLocalization = WorkAreaLocalization(
        targetPresent = true,
        confidence = Confidence(.95f),
        roi = NormalizedRoi(x, .30f, .20f, .30f),
    )

    private fun stamp(sequence: Long, atMonotonicMs: Long): FrameStamp = FrameStamp(
        capture = capture,
        sequence = sequence,
        receivedAt = ClockReading(atMonotonicMs, 1_700_000_000_000 + atMonotonicMs),
    )
}
