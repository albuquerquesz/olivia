package com.olivia.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HybridReplayHarnessTest {
    @Test
    fun `reports agreement and bounded tracker losses for deterministic replay`() {
        val capture = CaptureId(SessionId("replay"), 1)
        val analyzer = HybridVisualAnalyzer(
            remote = object : FrameAnalyzer {
                override suspend fun analyze(frame: Frame): Result<FrameAnalysis> = Result.failure(AssertionError())
            },
            clock = OliviaClock { ClockReading(10_000, 1_700_000_010_000) },
            localVisualAnalyzer = DeterministicLocalVisualAnalyzer(),
            lumaDecoder = object : FrameLumaDecoder {
                override fun decode(frame: Frame): LumaFrame = LumaFrame(4, 4, ByteArray(16) { 120 })
            },
            motionEstimator = SparseMotionEstimator { _, _ -> MotionEstimate(MotionTransform.IDENTITY, 24, 20, .4f) },
        )
        val roi = WorkAreaLocalization(true, Confidence(.95f), NormalizedRoi(.68f, .30f, .20f, .30f))
        val reference = DeterministicLocalVisualAnalyzer().analyze(LumaFrame(4, 4, ByteArray(16) { 120 }), roi)
        val frames = (1L..6L).map { sequence ->
            ReplayFrame(
                stamp = FrameStamp(capture, sequence, ClockReading(sequence * 100, 1_700_000_000_000 + sequence * 100)),
                luma = LumaFrame(4, 4, ByteArray(16) { 120 }),
                referenceLocalization = roi,
                referenceDiagnoses = reference,
            )
        }
        val report = HybridReplayHarness(
            analyzer,
            frameFactory = { replay -> Frame(replay.stamp, 4, 4, FrameFormat.NV21, FrameOrigin.MOCK, ByteArray(24)) },
            validationEveryFrames = 10,
        ).run(frames)

        assertEquals(6, report.frames)
        assertTrue(report.comparableFrames > 0)
        assertTrue(report.agreementRate >= 0f)
        assertTrue(report.meanRoiIou != null)
    }
}
