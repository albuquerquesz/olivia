package com.olivia.companion.session

import com.olivia.core.DiagnosisKind

/**
 * Fixture metadata for the offline demo. The marker is a synthetic byte, never an
 * image; it is consumed by [MockFrameAnalyzer] and discarded with the frame.
 */
internal data class MockFrameFixture(
    val offsetMs: Long,
    val marker: Int,
    val expectedKind: DiagnosisKind?,
)

internal object MockFrameFixtures {
    const val FRAME_WIDTH = 720
    const val FRAME_HEIGHT = 1280
    const val FRAME_INTERVAL_MS = 2_000L
    const val DEGRADED_GAP_START_MS = 50_000L
    const val DEGRADED_GAP_END_MS = 54_000L

    const val NORMAL_MARKER = 0
    const val FRAMING_MARKER = 1
    const val LIGHT_MARKER = 2
    const val HORIZON_MARKER = 3

    fun sequence(): List<MockFrameFixture> = generateSequence(0L) { it + FRAME_INTERVAL_MS }
        .takeWhile { it <= 60_000L }
        .filterNot { it in DEGRADED_GAP_START_MS until DEGRADED_GAP_END_MS }
        .map { offsetMs ->
            when (offsetMs) {
                12_000L -> MockFrameFixture(offsetMs, FRAMING_MARKER, DiagnosisKind.FRAMING)
                30_000L -> MockFrameFixture(offsetMs, LIGHT_MARKER, DiagnosisKind.LIGHT)
                40_000L -> MockFrameFixture(offsetMs, HORIZON_MARKER, DiagnosisKind.HORIZON)
                else -> MockFrameFixture(offsetMs, NORMAL_MARKER, null)
            }
        }
        .toList()
}
