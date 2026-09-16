package com.olivia.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LiveTrialReporterTest {
    @Test
    fun `keeps budget explicit and only reports observed battery deltas`() {
        val report = LiveTrialReporter.report(
            config = SessionConfig(),
            metrics = LiveLoopMetrics().snapshot(),
            phoneBatteryStart = BatteryObservation(80, "Moto G60"),
            phoneBatteryEnd = BatteryObservation(77, "Moto G60"),
        )

        assertEquals(0.5, report.samplingFps)
        assertEquals(2_000, report.analysisIntervalMs)
        assertEquals(4_000, report.correctionCooldownMs)
        assertEquals(3, report.phoneBatteryDeltaPercent)
        assertNull(report.glassesBatteryDeltaPercent)
    }
}
