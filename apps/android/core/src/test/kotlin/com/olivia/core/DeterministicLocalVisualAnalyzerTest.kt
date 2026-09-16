package com.olivia.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeterministicLocalVisualAnalyzerTest {
    private val analyzer = DeterministicLocalVisualAnalyzer()
    private val located = Confidence(0.9f)

    @Test
    fun `uses ROI offset bands for silent small and direct framing guidance`() {
        assertEquals(Severity.OK, framing(NormalizedRoi(0.4f, 0.3f, 0.2f, 0.3f)).severity)
        val slight = framing(NormalizedRoi(0.55f, 0.3f, 0.2f, 0.3f))
        assertEquals(Severity.MINOR, slight.severity)
        assertEquals(Direction.TURN_RIGHT, slight.direction)
        val large = framing(NormalizedRoi(0.72f, 0.3f, 0.2f, 0.3f))
        assertEquals(Severity.MAJOR, large.severity)
        assertEquals(Direction.TURN_RIGHT, large.direction)
    }

    @Test
    fun `measures dark ROI locally without claiming a hand caused it`() {
        val frame = luma(100, 100) { x, y -> if (x in 25..74 && y in 25..74) 30 else 210 }
        val diagnosis = analyzer.analyze(frame, location(NormalizedRoi(0.25f, 0.25f, 0.5f, 0.5f)))
            .first { it.kind == DiagnosisKind.LIGHT }
        assertEquals(Severity.MAJOR, diagnosis.severity)
        assertEquals(Direction.LEAVE_THE_SHADOW, diagnosis.direction)
    }

    @Test
    fun `abstains when locator confidence is insufficient or target is absent`() {
        val absent = analyzer.analyze(solid(100, 100, 200), WorkAreaLocalization(false, Confidence(0.9f), null))
        val uncertain = analyzer.analyze(solid(100, 100, 200), WorkAreaLocalization(true, Confidence(0.79f), NormalizedRoi(0.7f, 0.2f, 0.2f, 0.3f)))
        assertTrue(absent.isEmpty())
        assertTrue(uncertain.isEmpty())
    }

    @Test
    fun `finds a strongly tilted synthetic horizon`() {
        val frame = luma(160, 100) { x, y -> if (y >= 20 + x / 3) 30 else 220 }
        val diagnosis = analyzer.analyze(frame, location(NormalizedRoi(0.4f, 0.3f, 0.2f, 0.3f)))
            .first { it.kind == DiagnosisKind.HORIZON }
        assertEquals(Severity.MAJOR, diagnosis.severity)
        assertEquals(Direction.LEVEL_HORIZON_LEFT, diagnosis.direction)
    }

    private fun framing(roi: NormalizedRoi): VisualDiagnosis =
        analyzer.analyze(solid(100, 100, 200), location(roi)).first { it.kind == DiagnosisKind.FRAMING }

    private fun location(roi: NormalizedRoi) = WorkAreaLocalization(true, located, roi)
    private fun solid(width: Int, height: Int, value: Int): LumaFrame = luma(width, height) { _, _ -> value }
    private fun luma(width: Int, height: Int, pixel: (Int, Int) -> Int): LumaFrame =
        LumaFrame(width, height, ByteArray(width * height) { index -> pixel(index % width, index / width).toByte() })
}
