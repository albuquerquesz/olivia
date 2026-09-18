package com.olivia.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HybridVisualAnalyzerTest {
    private val capture = CaptureId(SessionId("hybrid"), 1)
    private val clock = TestClock()

    @Test
    fun `seeds from remote and classifies later frames locally`() {
        val analyzer = hybrid()
        val seed = stamp(1)
        analyzer.onRemoteLocalization(seed, localization(.68f))
        assertNull(analyzer.processFast(frame(stamp(1))))

        val result = requireNotNull(analyzer.processFast(frame(stamp(2)))).orThrow()
        assertEquals(AnalyzerSource.TRACKER_LOCAL, result.source)
        assertEquals(Direction.TURN_RIGHT, result.diagnoses.first { it.kind == DiagnosisKind.FRAMING }.direction)
    }

    @Test
    fun `low quality motion keeps output silent until reacquisition`() {
        val analyzer = hybrid(
            estimator = SparseMotionEstimator { _, _ -> MotionEstimate(MotionTransform.IDENTITY, 8, 1, 9f) },
        )
        analyzer.onRemoteLocalization(stamp(1), localization(.68f))
        analyzer.processFast(frame(stamp(1)))
        assertNull(analyzer.processFast(frame(stamp(2))))
    }

    @Test
    fun `delayed cloud localization reconciles tracker without frame retention`() {
        val analyzer = hybrid()
        analyzer.onRemoteLocalization(stamp(1), localization(.40f))
        analyzer.processFast(frame(stamp(1)))
        analyzer.processFast(frame(stamp(2)))
        analyzer.onRemoteLocalization(stamp(1), localization(.40f))
        val next = requireNotNull(analyzer.processFast(frame(stamp(3)))).orThrow()
        assertNotNull(next)
        assertTrue(next.frameStamp.sequence == 3L)
        assertFalse(next.source == AnalyzerSource.GEMINI_ROI_LOCAL)
    }

    private fun hybrid(
        estimator: SparseMotionEstimator = SparseMotionEstimator { _, _ ->
            MotionEstimate(MotionTransform.IDENTITY, 24, 20, .4f)
        },
    ): HybridVisualAnalyzer = HybridVisualAnalyzer(
        remote = object : FrameAnalyzer {
            override suspend fun analyze(frame: Frame): Result<FrameAnalysis> =
                Result.failure(IllegalStateException("unused"))
        },
        clock = clock,
        localVisualAnalyzer = DeterministicLocalVisualAnalyzer(),
        lumaDecoder = object : FrameLumaDecoder {
            override fun decode(frame: Frame): LumaFrame = LumaFrame(4, 4, ByteArray(16) { 120 })
        },
        motionEstimator = estimator,
    )

    private fun frame(stamp: FrameStamp): Frame = Frame(
        stamp = stamp,
        width = 4,
        height = 4,
        format = FrameFormat.NV21,
        origin = FrameOrigin.MOCK,
        bytes = ByteArray(24),
    )

    private fun localization(x: Float): WorkAreaLocalization = WorkAreaLocalization(
        targetPresent = true,
        confidence = Confidence(.95f),
        roi = NormalizedRoi(x, .30f, .20f, .30f),
    )

    private fun stamp(sequence: Long): FrameStamp = FrameStamp(
        capture = capture,
        sequence = sequence,
        receivedAt = ClockReading(sequence * 100, 1_700_000_000_000 + sequence * 100),
    )

    private fun Result<FrameAnalysis>.orThrow(): FrameAnalysis = getOrThrow()

    private class TestClock : OliviaClock {
        override fun now(): ClockReading = ClockReading(1_000, 1_700_000_001_000)
    }
}
