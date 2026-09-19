package com.olivia.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CancellationException

/** Local timeline implementation for the live loop and offline replay. */
public class InMemoryTimeline : Timeline {
    private val eventsBySession: MutableMap<SessionId, MutableList<TimelineEvent>> = mutableMapOf()

    override fun record(event: TimelineEvent) {
        val events = eventsBySession.getOrPut(event.sessionId) { mutableListOf() }
        if (events.none { it.id == event.id }) events += event
    }

    override fun events(sessionId: SessionId): List<TimelineEvent> =
        eventsBySession[sessionId].orEmpty().toList()
}

/**
 * State machine for the live Olivia session.
 *
 * Calls must be serialized by the Android lifecycle owner. It owns no DAT type and never starts
 * or imports a native recording: [GlassesGateway] is only the frame/connection boundary. The
 * adapter must feed [onCaptureHeartbeat] before it sends a frame to the sampler.
 */
public class SessionCoordinator(
    private val clock: OliviaClock,
    private val gateway: GlassesGateway,
    private val audio: AudioFeedback,
    private val timeline: Timeline,
) {
    private val captureFence = CaptureFence()
    private val _snapshot: MutableStateFlow<SessionSnapshot?> = MutableStateFlow(null)
    private var startedAt: ClockReading? = null
    private var capture: CaptureId? = null
    private var lastFrameMonotonicMs: Long? = null
    private var resumeRequested: Boolean = false
    private var eventSequence: Long = 0

    public val snapshot: StateFlow<SessionSnapshot?> = _snapshot

    /**
     * Allows the runtime sampler to reject callbacks from an old capture generation before
     * they reach analysis. The lifecycle owner must serialize this with all other calls here.
     */
    public fun accepts(frameStamp: FrameStamp): Boolean {
        val current = _snapshot.value ?: return false
        return captureFence.accepts(frameStamp, current.state) &&
            DegradationCause.CAPTURE_UNAVAILABLE !in current.degradationCauses
    }

    /** Records only structured visual output; frame bytes never cross this boundary. */
    public fun recordDiagnosis(analysis: FrameAnalysis) {
        if (!accepts(analysis.frameStamp)) return
        analysis.diagnoses.forEach { diagnosis ->
            record(
                type = TimelineEventType.DIAGNOSIS,
                sessionId = analysis.frameStamp.capture.sessionId,
                label = diagnosis.kind.name.lowercase(),
                diagnosis = diagnosis,
                direction = diagnosis.direction,
                confidence = diagnosis.confidence,
            )
        }
    }

    /** A correction enters the timeline only after [AudioFeedback] acknowledged playback start. */
    public fun recordCorrectionIssued(correction: Correction) {
        if (!accepts(FrameStamp(correction.capture, 0, correction.issuedAt))) return
        record(
            type = TimelineEventType.CORRECTION_ISSUED,
            sessionId = correction.capture.sessionId,
            // Keep the structured direction for consumers while exposing the local Portuguese
            // phrase as the human-readable review label.
            label = GuidanceCatalogue.phrase(correction.diagnosis, AudioMode.VOICE),
            diagnosis = correction.diagnosis,
            direction = correction.direction,
            confidence = correction.confidence,
            correctionStatus = CorrectionStatus.PENDING,
            correctionId = correction.id,
        )
    }

    /** Persists the later outcome that is safe to display in the local review. */
    public fun recordCorrectionOutcome(outcome: CorrectionOutcome) {
        val correction = outcome.correction
        record(
            type = TimelineEventType.CORRECTION_RESOLVED,
            sessionId = correction.capture.sessionId,
            label = outcome.status.name.lowercase(),
            diagnosis = correction.diagnosis,
            direction = correction.direction,
            confidence = correction.confidence,
            correctionStatus = outcome.status,
            correctionId = correction.id,
        )
    }

    /** Starts capture only; [onCaptureHeartbeat] is the evidence that makes it STARTED. */
    public suspend fun start(sessionId: SessionId, config: SessionConfig = SessionConfig()): Result<SessionSnapshot> {
        val current = _snapshot.value
        if (current?.id == sessionId && current.state in setOf(SessionState.STARTING, SessionState.STARTED)) {
            return Result.success(current)
        }
        if (current != null && current.state !in setOf(SessionState.IDLE, SessionState.STOPPED, SessionState.READY, SessionState.ERROR)) {
            return Result.failure(IllegalStateException("Outra sessão ainda está ativa"))
        }
        val now = clock.now()
        val newCapture = captureFence.open(sessionId)
        startedAt = now
        capture = newCapture
        lastFrameMonotonicMs = null
        resumeRequested = false
        setSnapshot(
            id = sessionId,
            state = SessionState.STARTING,
            config = config,
            started = now,
        )
        return try {
            gateway.startCapture(newCapture)
            // Startup may suspend for permission, connection and the first decoded frame.
            // Consume evidence already published by the adapter before the serialized owner
            // releases its lock to the watchdog or frame consumers.
            gateway.heartbeat.value?.takeIf { it.capture == newCapture }?.let {
                onCaptureHeartbeat(it)
            }
            Result.success(requireNotNull(_snapshot.value))
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            captureFence.invalidate()
            capture = null
            fail(sessionId, config, now, FailureCode.GLASSES_UNAVAILABLE)
            Result.failure(IllegalStateException("Não foi possível iniciar a captura"))
        }
    }

    /** Pause is idempotent and intentionally disarms frame health while delivery is suspended. */
    public suspend fun pause(): Result<SessionSnapshot> {
        val current = _snapshot.value ?: return Result.failure(IllegalStateException("Não há sessão"))
        if (current.state == SessionState.PAUSED) return Result.success(current)
        if (current.state !in setOf(SessionState.STARTED, SessionState.DEGRADED)) {
            return Result.failure(IllegalStateException("Sessão não pode ser pausada"))
        }
        captureFence.invalidate()
        return try {
            gateway.pauseCapture()
            resumeRequested = false
            setSnapshot(current.id, SessionState.PAUSED, current.config, requireStartedAt())
            record(TimelineEventType.SESSION_PAUSED, current.id, "pausou")
            signal(SessionSignal.SESSION_PAUSED)
            Result.success(requireNotNull(_snapshot.value))
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            Result.failure(IllegalStateException("Não foi possível pausar a captura"))
        }
    }

    /** Resume keeps PAUSED until a new-generation heartbeat proves that capture returned. */
    public suspend fun resume(): Result<SessionSnapshot> {
        val current = _snapshot.value ?: return Result.failure(IllegalStateException("Não há sessão"))
        if (current.state == SessionState.STARTED) return Result.success(current)
        if (current.state != SessionState.PAUSED) return Result.failure(IllegalStateException("Sessão não pode ser retomada"))
        val nextCapture = captureFence.open(current.id)
        capture = nextCapture
        return try {
            gateway.resumeCapture(nextCapture)
            resumeRequested = true
            Result.success(current)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            captureFence.invalidate()
            capture = null
            Result.failure(IllegalStateException("Não foi possível retomar a captura"))
        }
    }

    /** Stops the live loop; native-recording control is deliberately outside this coordinator. */
    public suspend fun stop(): Result<SessionSnapshot> {
        val current = _snapshot.value ?: return Result.failure(IllegalStateException("Não há sessão"))
        if (current.state == SessionState.STOPPED) return Result.success(current)
        if (current.state !in setOf(SessionState.STARTING, SessionState.STARTED, SessionState.PAUSED, SessionState.DEGRADED)) {
            return Result.failure(IllegalStateException("Sessão não pode ser encerrada"))
        }
        captureFence.invalidate()
        setSnapshot(current.id, SessionState.STOPPING, current.config, requireStartedAt())
        return try {
            gateway.stopCapture()
            signal(SessionSignal.SESSION_STOPPED)
            audio.stop()
            capture = null
            resumeRequested = false
            setSnapshot(current.id, SessionState.STOPPED, current.config, requireStartedAt())
            record(TimelineEventType.SESSION_STOPPED, current.id, "encerrou")
            Result.success(requireNotNull(_snapshot.value))
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            fail(current.id, current.config, requireStartedAt(), FailureCode.CAPTURE_FAILED)
            Result.failure(IllegalStateException("Não foi possível encerrar a captura"))
        }
    }

    /** Opens the post-take phase only after the live loop has definitely stopped. */
    public fun beginPostProcessing(): Result<SessionSnapshot> {
        val current = _snapshot.value ?: return Result.failure(IllegalStateException("Não há sessão"))
        if (current.state != SessionState.STOPPED) {
            return Result.failure(IllegalStateException("O take precisa estar encerrado"))
        }
        setSnapshot(current.id, SessionState.PROCESSING, current.config, requireStartedAt())
        return Result.success(requireNotNull(_snapshot.value))
    }

    /** Called only after the local transcript, alignment, and review were produced successfully. */
    public fun completePostProcessing(sessionId: SessionId): Result<SessionSnapshot> {
        val current = _snapshot.value ?: return Result.failure(IllegalStateException("Não há sessão"))
        if (current.id != sessionId || current.state != SessionState.PROCESSING) {
            return Result.failure(IllegalStateException("Revisão fora da sessão ativa"))
        }
        setSnapshot(current.id, SessionState.READY, current.config, requireStartedAt())
        return Result.success(requireNotNull(_snapshot.value))
    }

    /** Cancellation preserves the completed take and lets the creator retry processing later. */
    public fun cancelPostProcessing(sessionId: SessionId): Result<SessionSnapshot> {
        val current = _snapshot.value ?: return Result.failure(IllegalStateException("Não há sessão"))
        if (current.id != sessionId || current.state != SessionState.PROCESSING) {
            return Result.failure(IllegalStateException("Não há processamento para cancelar"))
        }
        setSnapshot(current.id, SessionState.STOPPED, current.config, requireStartedAt())
        return Result.success(requireNotNull(_snapshot.value))
    }

    /** A failed local provider is explicit and never yields a partial READY review. */
    public fun failPostProcessing(sessionId: SessionId): Result<SessionSnapshot> {
        val current = _snapshot.value ?: return Result.failure(IllegalStateException("Não há sessão"))
        if (current.id != sessionId || current.state != SessionState.PROCESSING) {
            return Result.failure(IllegalStateException("Revisão fora da sessão ativa"))
        }
        fail(current.id, current.config, requireStartedAt(), FailureCode.POST_PROCESSING_FAILED)
        return Result.success(requireNotNull(_snapshot.value))
    }

    /** Called by the capture callback independently of sampler or analyzer progress. */
    public suspend fun onCaptureHeartbeat(heartbeat: CaptureHeartbeat) {
        val current = _snapshot.value ?: return
        if (heartbeat.capture != capture) return
        if (DegradationCause.CAPTURE_UNAVAILABLE in current.degradationCauses &&
            !captureFence.accepts(FrameStamp(heartbeat.capture, 0, clock.now()), SessionState.DEGRADED)) return
        lastFrameMonotonicMs = heartbeat.receivedAtMonotonicMs
        when (current.state) {
            SessionState.STARTING -> {
                setSnapshot(current.id, SessionState.STARTED, current.config, requireStartedAt(), heartbeat.receivedAtMonotonicMs)
                record(TimelineEventType.SESSION_STARTED, current.id, "iniciou")
                signal(SessionSignal.SESSION_STARTED)
            }
            SessionState.PAUSED -> if (resumeRequested) {
                resumeRequested = false
                setSnapshot(current.id, SessionState.STARTED, current.config, requireStartedAt(), heartbeat.receivedAtMonotonicMs)
                record(TimelineEventType.SESSION_RESUMED, current.id, "retomou")
                signal(SessionSignal.SESSION_RESUMED)
            }
            SessionState.STARTED -> setSnapshot(
                current.id,
                SessionState.STARTED,
                current.config,
                requireStartedAt(),
                heartbeat.receivedAtMonotonicMs,
            )
            SessionState.DEGRADED -> recoverCapture(current, heartbeat)
            else -> Unit
        }
    }

    /** Checks the four-second capture watchdog. It deliberately ignores PAUSED. */
    public suspend fun checkCaptureHealth() {
        val current = _snapshot.value ?: return
        val now = clock.now()
        if (current.state == SessionState.STARTING && now.elapsedSince(requireStartedAt()) >= current.config.degradedAfterMs) {
            captureFence.invalidate()
            fail(current.id, current.config, requireStartedAt(), FailureCode.CAPTURE_FAILED)
            return
        }
        if (current.state !in setOf(SessionState.STARTED, SessionState.DEGRADED) ||
            current.resumeState == SessionState.PAUSED || DegradationCause.CAPTURE_UNAVAILABLE in current.degradationCauses) return
        val lastFrameAt = gateway.heartbeat.value?.takeIf { it.capture == capture }?.receivedAtMonotonicMs
            ?: lastFrameMonotonicMs
        val elapsed = lastFrameAt?.let { now.monotonicMs - it } ?: now.elapsedSince(requireStartedAt())
        if (elapsed >= current.config.degradedAfterMs) {
            captureFence.invalidate()
            degrade(DegradationCause.CAPTURE_UNAVAILABLE, SessionState.STARTED)
            record(TimelineEventType.SESSION_DEGRADED, current.id, "conexão indisponível")
            signal(SessionSignal.CONNECTION_LOST)
        }
    }

    /** Connection changes can degrade a paused session while preserving its paused intention. */
    public suspend fun onConnectionChanged(connection: GlassesConnection) {
        val current = _snapshot.value ?: return
        when (connection) {
            GlassesConnection.CONNECTED -> {
                // A connection callback alone does not prove that frames resumed. The following
                // heartbeat removes CAPTURE_UNAVAILABLE and emits CONNECTION_RESTORED.
            }
            GlassesConnection.DISCONNECTED, GlassesConnection.UNAVAILABLE -> {
                if (current.state in setOf(SessionState.STARTED, SessionState.PAUSED, SessionState.DEGRADED)) {
                    if (DegradationCause.CAPTURE_UNAVAILABLE in current.degradationCauses) return
                    captureFence.invalidate()
                    degrade(
                        DegradationCause.CAPTURE_UNAVAILABLE,
                        if (current.state == SessionState.PAUSED) SessionState.PAUSED else SessionState.STARTED,
                    )
                    record(TimelineEventType.SESSION_DEGRADED, current.id, "conexão indisponível")
                    signal(SessionSignal.CONNECTION_LOST)
                }
            }
        }
    }

    /** Reconnect with a fresh generation so callbacks from before the loss remain invalid. */
    public suspend fun retryCapture(): Result<Unit> {
        val current = _snapshot.value ?: return Result.failure(IllegalStateException("Não há sessão"))
        if (DegradationCause.CAPTURE_UNAVAILABLE !in current.degradationCauses) return Result.success(Unit)
        if (current.resumeState == SessionState.PAUSED) return Result.success(Unit)
        val next = captureFence.open(current.id)
        capture = next
        return try {
            gateway.resumeCapture(next)
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            captureFence.invalidate()
            Result.failure(IllegalStateException("Captura ainda indisponível"))
        }
    }

    /**
     * The current frame could not be classified. Capture remains active, but Olivia must make the
     * loss of live guidance explicit instead of silently treating it as a clean frame.
     */
    public suspend fun onAnalysisUnavailable() {
        val current = _snapshot.value ?: return
        if (current.state !in setOf(SessionState.STARTED, SessionState.PAUSED, SessionState.DEGRADED)) return
        if (DegradationCause.ANALYSIS_UNAVAILABLE in current.degradationCauses) return
        val intendedState = when (current.state) {
            SessionState.PAUSED -> SessionState.PAUSED
            SessionState.DEGRADED -> current.resumeState
            else -> SessionState.STARTED
        }
        degrade(DegradationCause.ANALYSIS_UNAVAILABLE, intendedState)
        record(TimelineEventType.SESSION_DEGRADED, current.id, "análise indisponível")
        signal(SessionSignal.ANALYSIS_UNAVAILABLE)
    }

    /** A later successful analysis, never a timer, is the proof that live guidance recovered. */
    public suspend fun onAnalysisRestored() {
        val current = _snapshot.value ?: return
        if (DegradationCause.ANALYSIS_UNAVAILABLE !in current.degradationCauses) return
        val remaining = current.degradationCauses - DegradationCause.ANALYSIS_UNAVAILABLE
        val recoveredState = if (remaining.isEmpty()) current.resumeState else SessionState.DEGRADED
        setSnapshot(
            current.id,
            recoveredState,
            current.config,
            requireStartedAt(),
            lastFrameMonotonicMs,
            remaining,
            current.resumeState,
        )
        if (remaining.isEmpty()) {
            record(TimelineEventType.SESSION_RECOVERED, current.id, "análise retomada")
            signal(SessionSignal.ANALYSIS_RESTORED)
        }
    }

    private suspend fun recoverCapture(current: SessionSnapshot, heartbeat: CaptureHeartbeat?) {
        val remaining = current.degradationCauses - DegradationCause.CAPTURE_UNAVAILABLE
        val recoveredState = if (remaining.isEmpty()) current.resumeState else SessionState.DEGRADED
        setSnapshot(
            current.id,
            recoveredState,
            current.config,
            requireStartedAt(),
            heartbeat?.receivedAtMonotonicMs ?: lastFrameMonotonicMs,
            remaining,
            current.resumeState,
        )
        if (remaining.isEmpty()) {
            record(TimelineEventType.SESSION_RECOVERED, current.id, "conexão retomada")
            signal(SessionSignal.CONNECTION_RESTORED)
        }
    }

    private fun degrade(cause: DegradationCause, resumeState: SessionState) {
        val current = requireNotNull(_snapshot.value)
        val causes = current.degradationCauses + cause
        setSnapshot(
            current.id,
            SessionState.DEGRADED,
            current.config,
            requireStartedAt(),
            lastFrameMonotonicMs,
            causes,
            if (current.state == SessionState.DEGRADED) current.resumeState else resumeState,
        )
    }

    private suspend fun signal(intent: SessionSignal) {
        val result = audio.signal(intent)
        if (result.isFailure) {
            val current = _snapshot.value ?: return
            if (current.state in setOf(SessionState.STARTED, SessionState.PAUSED, SessionState.DEGRADED)) {
                degrade(DegradationCause.AUDIO_UNAVAILABLE, current.state.takeIf { it == SessionState.PAUSED } ?: SessionState.STARTED)
                record(TimelineEventType.ERROR, current.id, "áudio indisponível")
            }
        }
    }

    private fun fail(id: SessionId, config: SessionConfig, started: ClockReading, code: FailureCode) {
        val now = clock.now()
        val current = _snapshot.value
        val preservedElapsed = current
            ?.takeIf { it.state in setOf(SessionState.PROCESSING, SessionState.READY, SessionState.STOPPED) }
            ?.elapsedMs
        _snapshot.value = SessionSnapshot(
            id = id,
            state = SessionState.ERROR,
            startedAtEpochMs = started.epochMs,
            elapsedMs = preservedElapsed ?: now.elapsedSince(started),
            config = config,
            lastFrameAtEpochMs = null,
            failure = OliviaFailure(code, "Falha controlada na sessão"),
        )
        record(TimelineEventType.ERROR, id, "falha de sessão")
    }

    private fun setSnapshot(
        id: SessionId,
        state: SessionState,
        config: SessionConfig,
        started: ClockReading,
        lastFrameAtMonotonicMs: Long? = null,
        causes: Set<DegradationCause> = emptySet(),
        resumeState: SessionState = SessionState.STARTED,
    ) {
        val now = clock.now()
        _snapshot.value = SessionSnapshot(
            id = id,
            state = state,
            startedAtEpochMs = started.epochMs,
            elapsedMs = now.elapsedSince(started),
            config = config,
            lastFrameAtEpochMs = lastFrameAtMonotonicMs?.let {
                started.epochMs + (it - started.monotonicMs)
            },
            degradationCauses = causes,
            resumeState = resumeState,
        )
    }

    private fun record(
        type: TimelineEventType,
        sessionId: SessionId,
        label: String,
        diagnosis: VisualDiagnosis? = null,
        direction: Direction? = null,
        confidence: Confidence? = null,
        correctionStatus: CorrectionStatus? = null,
        correctionId: String? = null,
    ) {
        val started = requireStartedAt()
        val now = clock.now()
        timeline.record(
            TimelineEvent(
                id = "${sessionId.value}:${++eventSequence}",
                sessionId = sessionId,
                atEpochMs = now.epochMs,
                offsetMs = now.elapsedSince(started),
                type = type,
                diagnosis = diagnosis,
                direction = direction,
                confidence = confidence,
                correctionStatus = correctionStatus,
                correctionId = correctionId,
                label = label,
            ),
        )
    }

    private fun requireStartedAt(): ClockReading = checkNotNull(startedAt) { "Sessão sem relógio inicial" }
}
