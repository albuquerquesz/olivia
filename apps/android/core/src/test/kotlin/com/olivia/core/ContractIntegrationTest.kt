package com.olivia.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContractIntegrationTest {
    @Test
    fun `golden timeline and review are readable and stable`() {
        val eventJson = fixture("timeline-v2.json")
        val event = ContractJson.decodeEvent(eventJson)
        assertEquals("correction-1", event.correctionId)
        assertEquals(14_000L, event.offsetMs)
        assertEquals(CorrectionStatus.OBEYED, event.correctionStatus)
        assertEquals(Json.parseToJsonElement(eventJson), Json.parseToJsonElement(ContractJson.encodeEvent(event)))

        val reviewJson = fixture("review-v2.json")
        val review = ContractJson.decodeReview(reviewJson)
        assertEquals(event, review.timeline.single())
        assertEquals(CutReason.SPEECH_RETAKE, review.suggestedCuts.single().reason)
        assertEquals(Json.parseToJsonElement(reviewJson), Json.parseToJsonElement(ContractJson.encodeReview(review)))
    }

    @Test
    fun `unsupported or absent versions cannot silently use Kotlin defaults`() {
        val event = fixture("timeline-v2.json")
        assertFailsWith<IllegalArgumentException> { ContractJson.decodeEvent(event.replace("2.0.0", "1.0.0")) }
        assertFailsWith<IllegalArgumentException> { ContractJson.decodeEvent(event.replace("2.0.0", "3.0.0")) }
        assertFailsWith<IllegalArgumentException> {
            ContractJson.decodeEvent(event.replace(",\n  \"contractVersion\": \"2.0.0\"", ""))
        }
        val review = ContractJson.decodeReview(fixture("review-v2.json"))
        assertFailsWith<IllegalArgumentException> {
            ContractJson.decodeReview(fixture("review-v2.json").replace(",\n    \"contractVersion\": \"2.0.0\"", ""))
        }
        assertFailsWith<IllegalArgumentException> {
            ContractJson.encodeReview(review.copy(sessionId = SessionId("other")))
        }
    }

    @Test
    fun `gateway analyzer fence and codec compose without SDK types`() = runBlocking {
        // As duas fontes têm formatos diferentes; o consumidor é o mesmo.
        val jpeg = composeFixture(FrameFormat.JPEG)
        val rgba = composeFixture(FrameFormat.RGBA_8888)
        assertEquals(jpeg, rgba)
        assertEquals(TimelineEventType.DIAGNOSIS, jpeg.type)
        assertEquals(DiagnosisKind.FRAMING, jpeg.diagnosis?.kind)
    }

    private suspend fun composeFixture(format: FrameFormat): TimelineEvent {
        val fence = CaptureFence()
        val capture = fence.open(SessionId("fixture-session"))
        val gateway: GlassesGateway = FixtureGateway(format)
        gateway.startCapture(capture)
        val frame = gateway.frames.toList().single()
        assertEquals(frame.stamp.receivedAt.monotonicMs, gateway.heartbeat.value?.receivedAtMonotonicMs)
        assertFalse(frame.toString().contains("PRIVATE_FIXTURE"))
        val analyzer: FrameAnalyzer = FixtureAnalyzer()
        val analysis = analyzer.analyze(frame).getOrThrow()
        assertTrue(fence.accepts(analysis.frameStamp, SessionState.STARTED))
        val event = TimelineEvent(
            id = "diagnosis-1",
            sessionId = analysis.frameStamp.capture.sessionId,
            atEpochMs = analysis.analyzedAt.epochMs,
            offsetMs = analysis.analyzedAt.elapsedSince(ClockReading(0, 1_000)),
            type = TimelineEventType.DIAGNOSIS,
            diagnosis = analysis.diagnoses.single(),
        )
        val roundTrip = ContractJson.decodeEvent(ContractJson.encodeEvent(event))
        gateway.stopCapture()
        fence.invalidate()
        assertFalse(fence.accepts(analysis.frameStamp, SessionState.STARTED))
        return roundTrip
    }

    private fun fixture(name: String): String = checkNotNull(javaClass.getResource("/contracts/$name"))
        .readText().replace("\r\n", "\n").trim()
}

/** Doubles de borda apenas; stamp, fence, validação e codec são implementações reais. */
private class FixtureGateway(private val format: FrameFormat) : GlassesGateway {
    override val connection: StateFlow<GlassesConnection> = MutableStateFlow(GlassesConnection.CONNECTED)
    override val heartbeat: MutableStateFlow<CaptureHeartbeat?> = MutableStateFlow(null)
    private var capture: CaptureId? = null

    override val frames: Flow<Frame> = flow {
        val current = checkNotNull(capture)
        val stamp = FrameStamp(current, 0, ClockReading(2_000, 3_000))
        heartbeat.value = CaptureHeartbeat(current, stamp.receivedAt.monotonicMs)
        emit(Frame(stamp, 720, 1280, format, FrameOrigin.MOCK, "PRIVATE_FIXTURE".encodeToByteArray()))
    }

    override suspend fun startCapture(capture: CaptureId) { this.capture = capture }
    override suspend fun pauseCapture() { capture = null }
    override suspend fun resumeCapture(capture: CaptureId) { this.capture = capture }
    override suspend fun stopCapture() { capture = null; heartbeat.value = null }
}

private class FixtureAnalyzer : FrameAnalyzer {
    override suspend fun analyze(frame: Frame): Result<FrameAnalysis> = Result.success(
        FrameAnalysis(
            frameStamp = frame.stamp,
            analyzedAt = ClockReading(2_100, 900), // relógio civil voltou, monotônico não
            latencyMs = 100,
            source = AnalyzerSource.MOCK,
            diagnoses = listOf(VisualDiagnosis(
                DiagnosisKind.FRAMING, Severity.MAJOR, Confidence(0.9f), Direction.TURN_RIGHT,
            )),
        ),
    )
}
