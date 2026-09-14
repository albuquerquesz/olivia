package com.olivia.core

/** Engineering defaults for local, deterministic guidance. */
public data class GuidancePolicy(
    val minimumConfidence: Confidence = Confidence(0.80f),
    val minimumSeverity: Severity = Severity.MAJOR,
    val cooldownMs: Long = 4_000,
) {
    init {
        require(cooldownMs >= 0)
    }
}

/** Local phrases only. Model output never becomes speech. */
public object GuidanceCatalogue {
    public fun phrase(direction: Direction, mode: AudioMode): String = if (mode == AudioMode.SIGNAL) {
        ""
    } else {
        when (direction) {
            Direction.TURN_LEFT -> "Vire para a esquerda."
            Direction.TURN_RIGHT -> "Vire para a direita."
            Direction.MOVE_CLOSER -> "Aproxime a câmera."
            Direction.MOVE_BACK -> "Afaste a câmera."
            Direction.RAISE -> "Suba um pouco a câmera."
            Direction.LOWER -> "Desça um pouco a câmera."
            Direction.FACE_THE_LIGHT -> "Busque mais luz na peça."
            Direction.AVOID_BACKLIGHT -> "Evite a luz atrás da peça."
            Direction.LEAVE_THE_SHADOW -> "A sua mão está fazendo sombra na peça."
            Direction.LEVEL_HORIZON_LEFT -> "Nivele o horizonte para a esquerda."
            Direction.LEVEL_HORIZON_RIGHT -> "Nivele o horizonte para a direita."
        }
    }
}

/**
 * Chooses one actionable diagnosis, with a global cooldown and no more than one pending
 * correction. Calls are serialized by the session loop.
 */
public class DefaultGuidanceEngine(
    private val mode: AudioMode,
    private val policy: GuidancePolicy = GuidancePolicy(),
) : GuidanceEngine {
    private var pendingId: String? = null
    private var lastIssuedAt: ClockReading? = null
    private var nextId: Long = 0

    override fun onAnalysis(analysis: FrameAnalysis, now: ClockReading): Correction? {
        if (pendingId != null) return null
        val last = lastIssuedAt
        if (last != null && now.elapsedSince(last) < policy.cooldownMs) return null
        val diagnosis = analysis.diagnoses
            .asSequence()
            .filter { it.severity.ordinal >= policy.minimumSeverity.ordinal }
            .filter { it.confidence.value >= policy.minimumConfidence.value }
            .filter { it.direction != null }
            .sortedWith(
                compareByDescending<VisualDiagnosis> { it.severity.ordinal }
                    .thenByDescending { it.confidence.value }
                    .thenBy { priority(it.kind) },
            )
            .firstOrNull()
            ?: return null
        val direction = requireNotNull(diagnosis.direction)
        val id = "${analysis.frameStamp.capture.sessionId.value}:${analysis.frameStamp.capture.generation}:${++nextId}"
        pendingId = id
        lastIssuedAt = now
        return Correction(
            id = id,
            capture = analysis.frameStamp.capture,
            issuedAt = now,
            diagnosis = diagnosis,
            direction = direction,
            confidence = diagnosis.confidence,
            spokenText = GuidanceCatalogue.phrase(direction, mode),
        )
    }

    override fun onCorrectionOutcome(correctionId: String, status: CorrectionStatus) {
        if (status != CorrectionStatus.PENDING && pendingId == correctionId) pendingId = null
    }

    private fun priority(kind: DiagnosisKind): Int = when (kind) {
        DiagnosisKind.FRAMING -> 0
        DiagnosisKind.LIGHT -> 1
        DiagnosisKind.HORIZON -> 2
    }
}

public data class CorrectionOutcome(
    val correction: Correction,
    val status: CorrectionStatus,
)

/**
 * Correlates an audio-start receipt with a later analysis from the same capture generation.
 * A timeout never confirms a correction: it becomes IGNORED only when a reliable later frame
 * still reports the deviation, otherwise EXPIRED.
 */
public class CorrectionTracker(
    private val confirmationConfidence: Confidence = Confidence(0.80f),
    private val confirmationWindowMs: Long = 8_000,
) {
    private var pending: Correction? = null
    private var receipt: AudioReceipt? = null

    init {
        require(confirmationWindowMs > 0)
    }

    /** A correction exists in the timeline only after audio playback actually started. */
    public fun begin(correction: Correction, audioReceipt: AudioReceipt): Boolean {
        if (pending != null || correction.status != CorrectionStatus.PENDING) return false
        if (audioReceipt.correctionId != correction.id || audioReceipt.capture != correction.capture) return false
        pending = correction
        receipt = audioReceipt
        return true
    }

    public fun onAnalysis(analysis: FrameAnalysis, now: ClockReading): CorrectionOutcome? {
        val correction = pending ?: return null
        val started = requireNotNull(receipt)
        if (analysis.frameStamp.capture != correction.capture) return null
        if (analysis.frameStamp.receivedAt.monotonicMs <= started.startedAt.monotonicMs) return null
        val diagnosis = analysis.diagnoses.firstOrNull { it.kind == correction.diagnosis.kind }
        if (diagnosis?.severity == Severity.OK && diagnosis.confidence.value >= confirmationConfidence.value) {
            return resolve(correction, CorrectionStatus.OBEYED)
        }
        if (now.elapsedSince(started.startedAt) < confirmationWindowMs) return null
        return when {
            diagnosis == null || diagnosis.confidence.value < confirmationConfidence.value ->
                resolve(correction, CorrectionStatus.EXPIRED)
            diagnosis.severity != Severity.OK -> resolve(correction, CorrectionStatus.IGNORED)
            else -> resolve(correction, CorrectionStatus.EXPIRED)
        }
    }

    /** Pause, degraded capture, stop or explicit cancellation never count as confirmation. */
    public fun expire(): CorrectionOutcome? = pending?.let { resolve(it, CorrectionStatus.EXPIRED) }

    private fun resolve(correction: Correction, status: CorrectionStatus): CorrectionOutcome {
        pending = null
        receipt = null
        return CorrectionOutcome(correction, status)
    }
}
