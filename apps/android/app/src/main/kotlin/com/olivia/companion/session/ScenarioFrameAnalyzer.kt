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

internal class ScenarioFrameAnalyzer(
    private val clock: com.olivia.core.OliviaClock = com.olivia.core.SystemOliviaClock(),
) : FrameAnalyzer {
    override suspend fun analyze(frame: Frame): Result<FrameAnalysis> {
        if (
            frame.origin != FrameOrigin.MOCK ||
            frame.width != SessionScenario.FRAME_WIDTH ||
            frame.height != SessionScenario.FRAME_HEIGHT ||
            frame.format != FrameFormat.RGBA_8888
        ) {
            return Result.failure(IllegalArgumentException("Frame de cenário offline inválido"))
        }

        val marker = frame.bytes.firstOrNull()?.toInt()
            ?: return Result.failure(IllegalArgumentException("Frame de cenário sem marcador"))
        val analyzedAt = clock.now()
        if (marker == SessionScenario.FRAMING_MARKER) {
            return analysisResult(
                frame = frame,
                analyzedAt = analyzedAt,
                diagnosis = VisualDiagnosis(
                    kind = DiagnosisKind.FRAMING,
                    severity = Severity.MAJOR,
                    confidence = Confidence(0.91f),
                    direction = Direction.TURN_RIGHT,
                ),
            )
        }
        if (marker == SessionScenario.LIGHT_MARKER) {
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
        if (marker == SessionScenario.HORIZON_MARKER) {
            return analysisResult(
                frame = frame,
                analyzedAt = analyzedAt,
                diagnosis = VisualDiagnosis(
                    kind = DiagnosisKind.HORIZON,
                    severity = Severity.MINOR,
                    confidence = Confidence(0.74f),
                    direction = Direction.LEVEL_HORIZON_LEFT,
                ),
            )
        }
        if (marker == SessionScenario.NORMAL_MARKER) {
            return analysisResult(frame = frame, analyzedAt = analyzedAt, diagnosis = null)
        }
        return Result.failure(IllegalArgumentException("Marcador de cenário desconhecido: $marker"))
    }

    private fun analysisResult(
        frame: Frame,
        analyzedAt: com.olivia.core.ClockReading,
        diagnosis: VisualDiagnosis?,
    ): Result<FrameAnalysis> = Result.success(
        FrameAnalysis(
            frameStamp = frame.stamp,
            analyzedAt = analyzedAt,
            latencyMs = analyzedAt.elapsedSince(frame.stamp.receivedAt),
            source = AnalyzerSource.MOCK,
            diagnoses = DiagnosisKind.entries.map { kind ->
                diagnosis?.takeIf { it.kind == kind }
                    ?: VisualDiagnosis(kind, Severity.OK, Confidence(0.95f), null)
            },
        ),
    )
}
