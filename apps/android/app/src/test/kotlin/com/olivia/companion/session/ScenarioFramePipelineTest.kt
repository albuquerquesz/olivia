package com.olivia.companion.session

import com.olivia.core.DiagnosisKind
import com.olivia.core.Frame
import com.olivia.core.FrameFormat
import com.olivia.core.FrameOrigin
import com.olivia.core.SessionConfig
import com.olivia.core.SessionId
import com.olivia.core.CaptureId
import com.olivia.core.ClockReading
import com.olivia.core.FrameStamp
import com.olivia.core.OliviaClock
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScenarioFramePipelineTest {
    @Test
    fun fixturesKeepTwoSecondCadenceAndReserveTheDegradedGap() {
        val fixtures = SessionScenario.sequence()

        assertEquals(29, fixtures.size)
        assertEquals(0L, fixtures.first().offsetMs)
        assertEquals(48_000L, fixtures[24].offsetMs)
        assertEquals(54_000L, fixtures[25].offsetMs)
        assertTrue(
            fixtures.zipWithNext().any { (previous, next) ->
                next.offsetMs - previous.offsetMs >= SessionConfig().degradedAfterMs
            },
        )
        assertEquals(DiagnosisKind.FRAMING, fixtures.first { it.offsetMs == 12_000L }.expectedKind)
        assertEquals(DiagnosisKind.LIGHT, fixtures.first { it.offsetMs == 30_000L }.expectedKind)
        assertEquals(DiagnosisKind.HORIZON, fixtures.first { it.offsetMs == 40_000L }.expectedKind)
    }

    @Test
    fun analyzerReturnsStructuredScenarioDiagnosisAndNoProblemForNormalFrame() = runTest {
        val analyzer = ScenarioFrameAnalyzer(OliviaClock { ClockReading(1_000L, 1_000L) })
        val sessionId = SessionId("scenario-test")

        val framing = analyzer.analyze(frame(sessionId, SessionScenario.FRAMING_MARKER)).getOrThrow()
        val normal = analyzer.analyze(frame(sessionId, SessionScenario.NORMAL_MARKER)).getOrThrow()

        assertEquals(DiagnosisKind.FRAMING, framing.diagnoses.single { it.severity != com.olivia.core.Severity.OK }.kind)
        assertEquals(com.olivia.core.AnalyzerSource.MOCK, framing.source)
        assertTrue(!normal.hasDeviation)
        assertEquals(3, normal.diagnoses.size)
    }

    @Test
    fun gatewayEmitsFramesAndResumesAfterPause() = runTest {
        var now = 10_000L
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gateway = ScenarioGlassesGateway(clock = OliviaClock { ClockReading(now, now) }, dispatcher = dispatcher)
        val frames = mutableListOf<Frame>()
        val collectJob = launch(dispatcher) {
            gateway.frames.take(2).collect { frames += it }
        }

        gateway.startCapture(CaptureId(SessionId("scenario-test"), 1))
        runCurrent()
        assertEquals(1, frames.size)
        assertEquals(10_000L, frames.first().stamp.receivedAt.epochMs)

        now += SessionScenario.FRAME_INTERVAL_MS
        advanceTimeBy(SessionScenario.FRAME_INTERVAL_MS)
        runCurrent()
        collectJob.join()
        assertEquals(2, frames.size)
        assertEquals(FrameOrigin.MOCK, frames.last().origin)

        gateway.pauseCapture()
        gateway.resumeCapture(CaptureId(SessionId("scenario-test"), 2))
        gateway.close()
    }

    private fun frame(sessionId: SessionId, marker: Int): Frame = Frame(
        stamp = FrameStamp(CaptureId(sessionId, 1), 0, ClockReading(0, 0)),
        width = SessionScenario.FRAME_WIDTH,
        height = SessionScenario.FRAME_HEIGHT,
        format = FrameFormat.RGBA_8888,
        origin = FrameOrigin.MOCK,
        bytes = byteArrayOf(marker.toByte()),
    )
}
