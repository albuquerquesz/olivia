package com.olivia.companion.session

import com.olivia.core.*

/** Compatibility name for scenario clients; uses the backend's shared thresholds/catalogue. */
internal class ThresholdGuidanceEngine(private val correctionCooldownMs: Long) : GuidanceEngine {
    private var delegate = engine()
    private fun engine() = DefaultGuidanceEngine(AudioMode.VOICE, GuidancePolicy(cooldownMs = correctionCooldownMs))
    override fun onAnalysis(analysis: FrameAnalysis, now: ClockReading): Correction? = delegate.onAnalysis(analysis, now)
    override fun onCorrectionOutcome(correctionId: String, status: CorrectionStatus) = delegate.onCorrectionOutcome(correctionId, status)
    fun reset() { delegate = engine() }
}
