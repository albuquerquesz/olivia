package com.olivia.companion.session

import com.olivia.core.AnalyzerSource
import com.olivia.core.Confidence
import com.olivia.core.Direction
import com.olivia.core.Frame
import com.olivia.core.FrameAnalysis
import com.olivia.core.FrameAnalyzer
import com.olivia.core.FrameFormat
import com.olivia.core.FrameOrigin
import com.olivia.core.VisualDiagnosis
import com.olivia.core.DiagnosisKind
import com.olivia.core.Severity

internal class MockFrameAnalyzer(
    private val clock: () -> Long = { System.currentTimeMillis() },
) : FrameAnalyzer {
    override suspend fun analyze(frame: Frame): Result<FrameAnalysis> {
        if (
            frame.origin != FrameOrigin.MOCK ||
            frame.width != MockFrameFixtures.FRAME_WIDTH ||
            frame.height != MockFrameFixtures.FRAME_HEIGHT ||
            frame.format != FrameFormat.RGBA_8888
        ) {
            return Result.failure(IllegalArgumentException("Fixture de frame mock inválida"))
        }

        val marker = frame.bytes.firstOrNull()?.toInt()
            ?: return Result.failure(IllegalArgumentException("Fixture mock sem marcador"))
        val analyzedAt = clock()
        if (marker == MockFrameFixtures.FRAMING_MARKER) {
            return analysisResult(
                frame = frame,
                analyzedAt = analyzedAt,
                diagnosis = VisualDiagnosis(
                    kind = DiagnosisKind.FRAMING,
                    severity = Severity.MAJOR,
                    confidence = Confidence(0.91f),
                    direction = Direction.TURN_RIGHT,
                    magnitude = 20.0,
                ),
            )
        }
        if (marker == MockFrameFixtures.LIGHT_MARKER) {
            return analysisResult(
                frame = frame,
                analyzedAt = analyzedAt,
                diagnosis = VisualDiagnosis(
                    kind = DiagnosisKind.LIGHT,
                    severity = Severity.MAJOR,
                    confidence = Confidence(0.88f),
                    direction = Direction.LEAVE_THE_SHADOW,
                ),
            )
        }
        if (marker == MockFrameFixtures.HORIZON_MARKER) {
            return analysisResult(
                frame = frame,
                analyzedAt = analyzedAt,
                diagnosis = VisualDiagnosis(
                    kind = DiagnosisKind.HORIZON,
                    severity = Severity.MINOR,
                    confidence = Confidence(0.74f),
                    direction = Direction.LEVEL_HORIZON_LEFT,
                    magnitude = 8.0,
                ),
            )
        }
        if (marker == MockFrameFixtures.NORMAL_MARKER) {
            return analysisResult(frame = frame, analyzedAt = analyzedAt, diagnosis = null)
        }
        return Result.failure(IllegalArgumentException("Marcador mock desconhecido: $marker"))
    }

    private fun analysisResult(
        frame: Frame,
        analyzedAt: Long,
        diagnosis: VisualDiagnosis?,
    ): Result<FrameAnalysis> = Result.success(
        FrameAnalysis(
            sessionId = frame.sessionId,
            frameCapturedAtEpochMs = frame.capturedAtEpochMs,
            analyzedAtEpochMs = analyzedAt,
            latencyMs = (analyzedAt - frame.capturedAtEpochMs).coerceAtLeast(0),
            source = AnalyzerSource.MOCK,
            diagnoses = listOfNotNull(diagnosis),
        ),
    )
}
