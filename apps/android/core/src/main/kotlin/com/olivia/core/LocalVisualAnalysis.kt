package com.olivia.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Decodes formats that are already uncompressed; Android handles JPEG before entering core. */
public interface FrameLumaDecoder {
    public fun decode(frame: Frame): LumaFrame?
}

public object LumaFrameDecoder : FrameLumaDecoder {
    override fun decode(frame: Frame): LumaFrame? = when (frame.format) {
        FrameFormat.NV21 -> {
            val size = frame.width * frame.height
            if (frame.bytes.size < size) null else LumaFrame(frame.width, frame.height, frame.bytes.copyOf(size))
        }
        FrameFormat.RGBA_8888 -> {
            val required = frame.width * frame.height * 4
            if (frame.bytes.size < required) null else {
                val luma = ByteArray(frame.width * frame.height)
                var source = 0
                for (index in luma.indices) {
                    val red = frame.bytes[source].toInt() and 0xff
                    val green = frame.bytes[source + 1].toInt() and 0xff
                    val blue = frame.bytes[source + 2].toInt() and 0xff
                    luma[index] = ((77 * red + 150 * green + 29 * blue) ushr 8).toByte()
                    source += 4
                }
                LumaFrame(frame.width, frame.height, luma)
            }
        }
        FrameFormat.JPEG -> null
    }
}

/** Pure, deterministic metrics for the three approved live diagnoses. */
public class DeterministicLocalVisualAnalyzer(
    private val lowLightLuma: Float = 82f,
    private val shadowContrastLuma: Float = 28f,
    private val centeredOffset: Float = 0.10f,
    private val largeOffset: Float = 0.25f,
    private val minimumLocalizationConfidence: Confidence = Confidence(0.80f),
) : LocalVisualAnalyzer {
    init {
        require(lowLightLuma in 1f..254f)
        require(shadowContrastLuma > 0f)
        require(centeredOffset in 0f..1f && largeOffset in centeredOffset..1f)
    }

    override fun analyze(luma: LumaFrame, localization: WorkAreaLocalization): List<VisualDiagnosis> {
        if (!localization.targetPresent || localization.confidence.value < minimumLocalizationConfidence.value) {
            return emptyList()
        }
        val roi = requireNotNull(localization.roi)
        return listOfNotNull(
            framing(roi, localization.confidence),
            light(luma, roi, localization.confidence),
            horizon(luma, localization.confidence),
        )
    }

    private fun framing(roi: NormalizedRoi, confidence: Confidence): VisualDiagnosis {
        val horizontal = roi.centerX - 0.5f
        val vertical = roi.centerY - 0.5f
        val useHorizontal = abs(horizontal) >= abs(vertical)
        val offset = abs(if (useHorizontal) horizontal else vertical)
        if (offset <= centeredOffset) return ok(DiagnosisKind.FRAMING, confidence)
        val direction = when {
            useHorizontal && horizontal > 0f -> Direction.TURN_RIGHT
            useHorizontal -> Direction.TURN_LEFT
            vertical > 0f -> Direction.LOWER
            else -> Direction.RAISE
        }
        return VisualDiagnosis(
            kind = DiagnosisKind.FRAMING,
            severity = if (offset <= largeOffset) Severity.MINOR else Severity.MAJOR,
            confidence = confidence,
            direction = direction,
        )
    }

    private fun light(luma: LumaFrame, roi: NormalizedRoi, confidence: Confidence): VisualDiagnosis {
        val bounds = roi.toPixelBounds(luma)
        val roiMean = mean(luma, bounds)
        val frameMean = mean(luma, PixelBounds(0, 0, luma.width, luma.height))
        val contrast = frameMean - roiMean
        return when {
            roiMean >= lowLightLuma -> ok(DiagnosisKind.LIGHT, confidence)
            contrast >= shadowContrastLuma -> VisualDiagnosis(
                DiagnosisKind.LIGHT,
                Severity.MAJOR,
                confidence,
                Direction.LEAVE_THE_SHADOW,
            )
            else -> VisualDiagnosis(
                DiagnosisKind.LIGHT,
                Severity.MINOR,
                confidence,
                Direction.FACE_THE_LIGHT,
            )
        }
    }

    private fun horizon(luma: LumaFrame, confidence: Confidence): VisualDiagnosis {
        val points = mutableListOf<EdgePoint>()
        val step = max(1, luma.width / 96)
        for (x in 1 until luma.width - 1 step step) {
            var bestY = -1
            var bestScore = 0
            for (y in 1 until luma.height - 1) {
                val gx = luma.at(x + 1, y) - luma.at(x - 1, y)
                val gy = luma.at(x, y + 1) - luma.at(x, y - 1)
                val score = abs(gy) - abs(gx)
                if (score > bestScore) {
                    bestScore = score
                    bestY = y
                }
            }
            if (bestScore >= HORIZON_EDGE_THRESHOLD && bestY >= 0) points += EdgePoint(x.toFloat(), bestY.toFloat())
        }
        if (points.size < MIN_HORIZON_POINTS) return VisualDiagnosis(
            DiagnosisKind.HORIZON, Severity.OK, Confidence(0f), null,
        )
        val regression = regression(points) ?: return VisualDiagnosis(DiagnosisKind.HORIZON, Severity.OK, Confidence(0f), null)
        val localConfidence = Confidence(min(confidence.value, regression.confidence))
        if (localConfidence.value < minimumLocalizationConfidence.value) {
            return VisualDiagnosis(DiagnosisKind.HORIZON, Severity.OK, localConfidence, null)
        }
        val normalizedSlope = abs(regression.slope) * luma.width / luma.height
        if (normalizedSlope <= HORIZON_LEVEL_SLOPE) return ok(DiagnosisKind.HORIZON, localConfidence)
        return VisualDiagnosis(
            kind = DiagnosisKind.HORIZON,
            severity = if (normalizedSlope <= HORIZON_MAJOR_SLOPE) Severity.MINOR else Severity.MAJOR,
            confidence = localConfidence,
            direction = if (regression.slope > 0f) Direction.LEVEL_HORIZON_LEFT else Direction.LEVEL_HORIZON_RIGHT,
        )
    }

    private fun ok(kind: DiagnosisKind, confidence: Confidence): VisualDiagnosis =
        VisualDiagnosis(kind, Severity.OK, confidence, null)

    private fun NormalizedRoi.toPixelBounds(frame: LumaFrame): PixelBounds {
        val left = (x * frame.width).toInt().coerceIn(0, frame.width - 1)
        val top = (y * frame.height).toInt().coerceIn(0, frame.height - 1)
        val right = ((x + width) * frame.width).toInt().coerceIn(left + 1, frame.width)
        val bottom = ((y + height) * frame.height).toInt().coerceIn(top + 1, frame.height)
        return PixelBounds(left, top, right, bottom)
    }

    private fun mean(frame: LumaFrame, bounds: PixelBounds): Float {
        var sum = 0L
        var count = 0
        for (y in bounds.top until bounds.bottom) for (x in bounds.left until bounds.right) {
            sum += frame.at(x, y)
            count += 1
        }
        return sum.toFloat() / count
    }

    private fun regression(points: List<EdgePoint>): Regression? {
        val n = points.size.toFloat()
        val meanX = points.sumOf { it.x.toDouble() }.toFloat() / n
        val meanY = points.sumOf { it.y.toDouble() }.toFloat() / n
        val xx = points.sumOf { ((it.x - meanX) * (it.x - meanX)).toDouble() }.toFloat()
        if (xx <= 0f) return null
        val slope = points.sumOf { ((it.x - meanX) * (it.y - meanY)).toDouble() }.toFloat() / xx
        val residual = points.sumOf { ((it.y - meanY - slope * (it.x - meanX)).let { it * it }).toDouble() }.toFloat()
        val variance = points.sumOf { ((it.y - meanY).let { it * it }).toDouble() }.toFloat()
        val confidence = if (variance <= 0f) 0f else (1f - residual / variance).coerceIn(0f, 1f)
        return Regression(slope, confidence)
    }

    private data class PixelBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)
    private data class EdgePoint(val x: Float, val y: Float)
    private data class Regression(val slope: Float, val confidence: Float)

    private companion object {
        const val HORIZON_EDGE_THRESHOLD = 36
        const val MIN_HORIZON_POINTS = 12
        const val HORIZON_LEVEL_SLOPE = 0.03f
        const val HORIZON_MAJOR_SLOPE = 0.07f
    }
}
