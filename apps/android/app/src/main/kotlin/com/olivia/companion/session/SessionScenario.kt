package com.olivia.companion.session

import com.olivia.core.DiagnosisKind

/**
 * Scenario metadata for the offline runtime. The marker is a synthetic byte, never an
 * image; it is consumed by [ScenarioFrameAnalyzer] and discarded with the frame.
 */
internal data class ScenarioFrame(
    val offsetMs: Long,
    val marker: Int,
    val expectedKind: DiagnosisKind?,
)

internal object SessionScenario {
    const val FRAME_WIDTH = 720
    const val FRAME_HEIGHT = 1280
    const val FRAME_INTERVAL_MS = 2_000L
    const val DEGRADED_GAP_START_MS = 50_000L
    const val DEGRADED_GAP_END_MS = 54_000L

    const val NORMAL_MARKER = 0
    const val FRAMING_MARKER = 1
    const val LIGHT_MARKER = 2
    const val HORIZON_MARKER = 3

    fun sequence(): List<ScenarioFrame> = generateSequence(0L) { it + FRAME_INTERVAL_MS }
        .takeWhile { it <= 60_000L }
        .filterNot { it in DEGRADED_GAP_START_MS until DEGRADED_GAP_END_MS }
        .map(::frameAt)
        .toList()

    private fun frameAt(offsetMs: Long): ScenarioFrame {
        if (offsetMs == 12_000L) {
            return ScenarioFrame(offsetMs, FRAMING_MARKER, DiagnosisKind.FRAMING)
        }
        if (offsetMs == 30_000L) {
            return ScenarioFrame(offsetMs, LIGHT_MARKER, DiagnosisKind.LIGHT)
        }
        if (offsetMs == 40_000L) {
            return ScenarioFrame(offsetMs, HORIZON_MARKER, DiagnosisKind.HORIZON)
        }
        return ScenarioFrame(offsetMs, NORMAL_MARKER, null)
    }
}
