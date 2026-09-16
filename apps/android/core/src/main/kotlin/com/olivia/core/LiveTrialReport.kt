package com.olivia.core

/** A manually observed battery level. It is deliberately not inferred from Android APIs. */
public data class BatteryObservation(
    val percent: Int,
    val label: String,
) {
    init {
        require(percent in 0..100)
        require(label.isNotBlank())
    }
}

/**
 * Sanitized, in-memory handoff for a measured demo run. No session identifier, frame, model
 * response, audio, device serial, network name or credentials is represented here.
 */
public data class LiveTrialReport(
    val samplingFps: Double,
    val analysisIntervalMs: Long,
    val correctionCooldownMs: Long,
    val degradedAfterMs: Long,
    val metrics: LiveLoopMetricSnapshot,
    val phoneBatteryStart: BatteryObservation?,
    val phoneBatteryEnd: BatteryObservation?,
    val glassesBatteryStart: BatteryObservation?,
    val glassesBatteryEnd: BatteryObservation?,
) {
    public val phoneBatteryDeltaPercent: Int? get() = delta(phoneBatteryStart, phoneBatteryEnd)
    public val glassesBatteryDeltaPercent: Int? get() = delta(glassesBatteryStart, glassesBatteryEnd)

    private fun delta(start: BatteryObservation?, end: BatteryObservation?): Int? =
        if (start == null || end == null) null else start.percent - end.percent
}

/** Builds an explicit report; callers must never manufacture a battery measurement. */
public object LiveTrialReporter {
    public fun report(
        config: SessionConfig,
        metrics: LiveLoopMetricSnapshot,
        phoneBatteryStart: BatteryObservation? = null,
        phoneBatteryEnd: BatteryObservation? = null,
        glassesBatteryStart: BatteryObservation? = null,
        glassesBatteryEnd: BatteryObservation? = null,
    ): LiveTrialReport = LiveTrialReport(
        samplingFps = config.samplingFps,
        analysisIntervalMs = config.analysisIntervalMs,
        correctionCooldownMs = config.correctionCooldownMs,
        degradedAfterMs = config.degradedAfterMs,
        metrics = metrics,
        phoneBatteryStart = phoneBatteryStart,
        phoneBatteryEnd = phoneBatteryEnd,
        glassesBatteryStart = glassesBatteryStart,
        glassesBatteryEnd = glassesBatteryEnd,
    )
}
