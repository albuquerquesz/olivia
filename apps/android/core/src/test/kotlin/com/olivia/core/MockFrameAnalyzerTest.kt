package com.olivia.core

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MockFrameAnalyzerTest {
    @Test
    fun `synthetic fixture catalogue covers positive negative and ambiguous targets`() {
        val fixtures = listOf(
            MockVisualFixtures.centered,
            MockVisualFixtures.subjectRight,
            MockVisualFixtures.handShadow,
            MockVisualFixtures.lowLight,
            MockVisualFixtures.tiltedHorizon,
            MockVisualFixtures.targetAbsent,
        )

        assertTrue(fixtures.all { it.licensedForTesting && it.width == 720 && it.height == 1_280 })
        assertTrue(fixtures.any { it.expectedDiagnoses.any { diagnosis -> diagnosis.kind == DiagnosisKind.FRAMING } })
        assertTrue(fixtures.any { it.expectedDiagnoses.any { diagnosis -> diagnosis.kind == DiagnosisKind.LIGHT } })
        assertTrue(fixtures.any { it.expectedDiagnoses.any { diagnosis -> diagnosis.kind == DiagnosisKind.HORIZON } })
        assertTrue(MockVisualFixtures.targetAbsent.expectedDiagnoses.isEmpty())
        assertEquals(null, MockVisualFixtures.targetAbsent.expectedRoi)
        assertTrue(MockVisualFixtures.subjectRight.expectedRoi != null)
    }

    @Test
    fun `returns only the configured synthetic diagnosis`() = runBlocking {
        val clock = TestClock(1_000)
        val analyzer = MockFrameAnalyzer(
            clock,
            mapOf(
                4L to MockAnalysisReply.Diagnosis(
                    listOf(
                        VisualDiagnosis(
                            kind = DiagnosisKind.FRAMING,
                            severity = Severity.MAJOR,
                            confidence = Confidence(0.9f),
                            direction = Direction.TURN_RIGHT,
                        ),
                    ),
                ),
            ),
        )

        val result = analyzer.analyze(frame(4, clock))
        assertTrue(result.isSuccess)
        assertEquals(AnalyzerSource.MOCK, result.getOrThrow().source)
        assertEquals(Direction.TURN_RIGHT, result.getOrThrow().diagnoses.single().direction)
    }

    @Test
    fun `configured failures remain failures and never expose bytes`() = runBlocking {
        val clock = TestClock(1_000)
        val analyzer = MockFrameAnalyzer(clock, mapOf(2L to MockAnalysisReply.Failure("rate limited")))

        val result = analyzer.analyze(frame(2, clock))
        assertFalse(result.isSuccess)
        assertEquals("rate limited", result.exceptionOrNull()?.message)
    }

    private fun frame(sequence: Long, clock: TestClock): Frame = Frame(
        stamp = FrameStamp(CaptureId(SessionId("mock"), 1), sequence, clock.now()),
        width = 720,
        height = 1_280,
        format = FrameFormat.JPEG,
        origin = FrameOrigin.MOCK,
        bytes = byteArrayOf(1, 2, 3),
    )
}

private class TestClock(private val monotonicMs: Long) : OliviaClock {
    override fun now(): ClockReading = ClockReading(monotonicMs, 1_700_000_000_000 + monotonicMs)
}
