package com.olivia.core

import java.nio.file.Files
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The persistent, offline proof for the live correction loop. It stores only its safe timeline
 * metadata; test frames themselves are empty and never reach the repository.
 */
class GuidanceTimelineIntegrationTest {
    @Test
    fun `two matching diagnoses lead to an audible correction confirmed by a later clean frame`() {
        val directory = Files.createTempDirectory("olivia-guidance-integration-")
        try {
            val session = SessionId("guidance-integration")
            val capture = CaptureId(session, 1)
            val guidance = DefaultGuidanceEngine(AudioMode.VOICE)
            val tracker = CorrectionTracker()
            val timeline = FileTimeline(directory)

            assertNull(guidance.onAnalysis(deviation(capture, sequence = 0, atMs = 0), reading(0)))
            val correction = requireNotNull(guidance.onAnalysis(deviation(capture, sequence = 1, atMs = 2_000), reading(2_000)))
            val receipt = AudioReceipt(correction.id, capture, reading(2_000))
            assertTrue(tracker.begin(correction, receipt))
            timeline.record(
                TimelineEvent(
                    id = "issued-${correction.id}",
                    sessionId = session,
                    atEpochMs = receipt.startedAt.epochMs,
                    offsetMs = receipt.startedAt.monotonicMs,
                    type = TimelineEventType.CORRECTION_ISSUED,
                    diagnosis = correction.diagnosis,
                    direction = correction.direction,
                    confidence = correction.confidence,
                    correctionStatus = CorrectionStatus.PENDING,
                    correctionId = correction.id,
                    label = "corrija o enquadramento",
                ),
            )

            val outcome = assertNotNull(tracker.onAnalysis(clean(capture, sequence = 2, atMs = 4_000), reading(4_000)))
            assertEquals(CorrectionStatus.OBEYED, outcome.status)
            guidance.onCorrectionOutcome(correction.id, outcome.status)
            timeline.record(
                TimelineEvent(
                    id = "resolved-${correction.id}",
                    sessionId = session,
                    atEpochMs = 1_700_000_004_000,
                    offsetMs = 4_000,
                    type = TimelineEventType.CORRECTION_RESOLVED,
                    diagnosis = correction.diagnosis,
                    direction = correction.direction,
                    confidence = correction.confidence,
                    correctionStatus = outcome.status,
                    correctionId = correction.id,
                    label = "recentrou",
                ),
            )

            val persisted = FileTimeline(directory).events(session)
            assertEquals(listOf(TimelineEventType.CORRECTION_ISSUED, TimelineEventType.CORRECTION_RESOLVED), persisted.map(TimelineEvent::type))
            assertTrue(persisted.all { it.label != null && it.label!!.none(Char::isDigit) })
        } finally {
            Files.walk(directory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }

    private fun deviation(capture: CaptureId, sequence: Long, atMs: Long): FrameAnalysis = analysis(
        capture = capture,
        sequence = sequence,
        atMs = atMs,
        diagnosis = VisualDiagnosis(DiagnosisKind.FRAMING, Severity.MAJOR, Confidence(0.9f), Direction.TURN_RIGHT),
    )

    private fun clean(capture: CaptureId, sequence: Long, atMs: Long): FrameAnalysis = analysis(
        capture = capture,
        sequence = sequence,
        atMs = atMs,
        diagnosis = VisualDiagnosis(DiagnosisKind.FRAMING, Severity.OK, Confidence(0.9f), null),
    )

    private fun analysis(capture: CaptureId, sequence: Long, atMs: Long, diagnosis: VisualDiagnosis): FrameAnalysis = FrameAnalysis(
        frameStamp = FrameStamp(capture, sequence, reading(atMs)),
        analyzedAt = reading(atMs),
        latencyMs = 0,
        source = AnalyzerSource.MOCK,
        diagnoses = listOf(diagnosis),
    )

    private fun reading(atMs: Long): ClockReading = ClockReading(atMs, 1_700_000_000_000 + atMs)
}
