package com.olivia.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FrameSamplerTest {
    @Test
    fun `keeps only the newest pending frame while analysis is in flight`() {
        val clock = MutableClock()
        val sampler = FrameSampler(clock)
        val capture = CaptureId(SessionId("session"), 1)

        assertIs<SamplingDecision.Analyze>(sampler.offer(frame(capture, 0, clock), accepts = { true }).single())
        assertEquals(1, sampler.offer(frame(capture, 1, clock), accepts = { true }).size)
        val replacement = sampler.offer(frame(capture, 2, clock), accepts = { true })
        val discarded = assertIs<SamplingDecision.Discard>(replacement.first())
        assertEquals(1, discarded.frame.stamp.sequence)
        assertEquals(FrameDiscardReason.REPLACED_BY_NEWER_FRAME, discarded.reason)

        clock.advanceBy(2_000)
        val next = sampler.completeAnalysis().single()
        assertEquals(2, assertIs<SamplingDecision.Analyze>(next).frame.stamp.sequence)
    }

    @Test
    fun `does not analyze expired or invalidated frames`() {
        val clock = MutableClock()
        val sampler = FrameSampler(clock, maxFrameAgeMs = 2_000)
        val capture = CaptureId(SessionId("session"), 1)
        val stale = frame(capture, 0, clock)
        clock.advanceBy(2_001)

        val expired = assertIs<SamplingDecision.Discard>(sampler.offer(stale, accepts = { true }).single())
        assertEquals(FrameDiscardReason.EXPIRED, expired.reason)
        val inactive = assertIs<SamplingDecision.Discard>(
            sampler.offer(frame(capture, 1, clock), accepts = { false }).single(),
        )
        assertEquals(FrameDiscardReason.INACTIVE_CAPTURE, inactive.reason)
    }

    @Test
    fun `cadence waits for two seconds without changing capture health`() {
        val clock = MutableClock()
        val sampler = FrameSampler(clock)
        val capture = CaptureId(SessionId("session"), 1)
        sampler.offer(frame(capture, 0, clock), accepts = { true })
        sampler.completeAnalysis()
        sampler.offer(frame(capture, 1, clock), accepts = { true })

        assertIs<SamplingDecision.Waiting>(sampler.poll().single())
        clock.advanceBy(2_000)
        assertEquals(1, assertIs<SamplingDecision.Analyze>(sampler.poll().single()).frame.stamp.sequence)
    }

    private fun frame(capture: CaptureId, sequence: Long, clock: MutableClock): Frame = Frame(
        stamp = FrameStamp(capture, sequence, clock.now()),
        width = 720,
        height = 1_280,
        format = FrameFormat.JPEG,
        origin = FrameOrigin.MOCK,
        bytes = byteArrayOf(0),
    )
}

private class MutableClock : OliviaClock {
    private var monotonicMs: Long = 0

    override fun now(): ClockReading = ClockReading(monotonicMs, 1_700_000_000_000 + monotonicMs)

    fun advanceBy(durationMs: Long) {
        monotonicMs += durationMs
    }
}
