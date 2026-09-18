package com.olivia.companion.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.olivia.core.AudioFeedback
import com.olivia.core.AudioMode
import com.olivia.core.AudioReceipt
import com.olivia.core.ClockReading
import com.olivia.core.Correction
import com.olivia.core.Direction
import com.olivia.core.OliviaClock
import com.olivia.core.SessionSignal
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

public enum class AudioTimingStage {
    TTS_INIT,
    TTS_REQUEST_TO_START,
    SIGNAL_REQUEST_TO_START,
}

public fun interface AudioTimingSink {
    public fun onStage(stage: AudioTimingStage, durationMs: Long)
}

/**
 * Android boundary for Olivia's only live output channel.
 *
 * Voice uses the device TTS engine configured for pt-BR. Signal uses generated, in-memory PCM
 * for an 80 ms stereo tone; it creates no media file, makes no network request and keeps no
 * correction text. Bluetooth routing is intentionally left to Android's selected media output,
 * which is what can route sound to the Ray-Ban speakers when paired.
 */
class AndroidAudioFeedback(
    context: Context,
    private val clock: OliviaClock,
    initialMode: AudioMode,
    private val timingSink: AudioTimingSink = AudioTimingSink { _, _ -> },
) : com.olivia.companion.session.AudioFeedbackController, AutoCloseable {
    private val audioStatus = kotlinx.coroutines.flow.MutableStateFlow(com.olivia.companion.session.AudioFeedbackStatus.INITIALIZING)
    private val audioMessage = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    override val status: kotlinx.coroutines.flow.StateFlow<com.olivia.companion.session.AudioFeedbackStatus> = audioStatus
    override val statusMessage: kotlinx.coroutines.flow.StateFlow<String?> = audioMessage

    @Volatile
    private var activeMode: AudioMode = initialMode

    override val mode: AudioMode get() = activeMode
    private val mainHandler = Handler(Looper.getMainLooper())
    private val initialized: CompletableDeferred<Result<Unit>> = CompletableDeferred()
    private data class PendingUtterance(
        val capture: com.olivia.core.CaptureId,
        val continuation: kotlinx.coroutines.CancellableContinuation<Result<AudioReceipt>>,
        val requestedAtNanos: Long,
    )

    private val pending: ConcurrentHashMap<String, PendingUtterance> = ConcurrentHashMap()
    private val signalTracks: ConcurrentHashMap<SignalDirection, AudioTrack> = ConcurrentHashMap()
    private lateinit var textToSpeech: TextToSpeech
    private val ttsInitStartedNanos = System.nanoTime()

    init {
        textToSpeech = TextToSpeech(context.applicationContext) { status ->
            if (status != TextToSpeech.SUCCESS) {
                emitTiming(AudioTimingStage.TTS_INIT, elapsedMs(ttsInitStartedNanos))
                markInitialized(Result.failure(IllegalStateException("TTS indisponível")))
            } else {
                // The init callback can theoretically arrive while the property assignment is
                // still unwinding. Posting guarantees the instance is fully assigned first.
                mainHandler.post {
                    val availability = textToSpeech.setLanguage(Locale("pt", "BR"))
                    if (availability < TextToSpeech.LANG_AVAILABLE) {
                        emitTiming(AudioTimingStage.TTS_INIT, elapsedMs(ttsInitStartedNanos))
                        markInitialized(Result.failure(IllegalStateException("Voz pt-BR indisponível")))
                    } else {
                        emitTiming(AudioTimingStage.TTS_INIT, elapsedMs(ttsInitStartedNanos))
                        markInitialized(Result.success(Unit))
                    }
                }
            }
        }
        textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {
                val utterance = pending.remove(utteranceId) ?: return
                emitTiming(
                    AudioTimingStage.TTS_REQUEST_TO_START,
                    elapsedMs(utterance.requestedAtNanos),
                )
                utterance.continuation.resume(
                    Result.success(
                        AudioReceipt(
                            correctionId = utteranceId,
                            capture = utterance.capture,
                            startedAt = clock.now(),
                        ),
                    ),
                )
            }

            override fun onDone(utteranceId: String) = Unit

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) {
                pending.remove(utteranceId)?.continuation?.resume(Result.failure(IllegalStateException("TTS falhou")))
            }

            override fun onError(utteranceId: String, errorCode: Int) {
                pending.remove(utteranceId)?.continuation?.resume(Result.failure(IllegalStateException("TTS falhou")))
            }
        })
    }

    private fun markInitialized(result: Result<Unit>) {
        initialized.complete(result)
        audioStatus.value = if (result.isSuccess) com.olivia.companion.session.AudioFeedbackStatus.READY
            else com.olivia.companion.session.AudioFeedbackStatus.ERROR
        audioMessage.value = if (result.isFailure) "Voz indisponível; as orientações usam sinais locais." else null
    }

    override suspend fun announce(correction: Correction): Result<AudioReceipt> = when (mode) {
        AudioMode.SIGNAL -> playSignal(SignalTone.directionFor(correction.direction)).map {
            AudioReceipt(correction.id, correction.capture, it)
        }
        AudioMode.VOICE -> {
            val spoken = kotlinx.coroutines.withTimeoutOrNull(5_000) { speak(correction) }
                ?: Result.failure(IllegalStateException("TTS não iniciou a fala"))
            if (spoken.isSuccess) {
                audioStatus.value = com.olivia.companion.session.AudioFeedbackStatus.READY
                audioMessage.value = null
                spoken
            } else {
                audioStatus.value = com.olivia.companion.session.AudioFeedbackStatus.ERROR
                audioMessage.value = "Voz indisponível; as orientações usam sinais locais."
                playSignal(SignalTone.directionFor(correction.direction)).map {
                    AudioReceipt(correction.id, correction.capture, it)
                }
            }
        }
    }

    override suspend fun signal(signal: SessionSignal): Result<ClockReading> =
        playSignal(SignalTone.directionFor(signal))

    override suspend fun stop() {
        pending.values.forEach { utterance ->
            utterance.continuation.resume(Result.failure(IllegalStateException("Áudio interrompido")))
        }
        pending.clear()
        mainHandler.post { textToSpeech.stop() }
    }

    /** The preference is applied between commands; no audio content is persisted. */
    fun setMode(mode: AudioMode) {
        activeMode = mode
    }

    override fun close() {
        pending.clear()
        mainHandler.post {
            textToSpeech.stop()
            textToSpeech.shutdown()
            signalTracks.values.forEach { track -> runCatching { track.stop(); track.release() } }
            signalTracks.clear()
        }
    }

    private suspend fun speak(correction: Correction): Result<AudioReceipt> {
        val ready = kotlinx.coroutines.withTimeoutOrNull(5_000) { initialized.await() }
            ?: return Result.failure(IllegalStateException("TTS indisponível"))
        if (ready.isFailure) return Result.failure(ready.exceptionOrNull()!!)
        if (correction.spokenText.isBlank()) return Result.failure(IllegalArgumentException("Correção sem frase"))
        return suspendCancellableCoroutine { continuation ->
            val id = correction.id
            pending[id] = PendingUtterance(correction.capture, continuation, System.nanoTime())
            mainHandler.post {
                if (!continuation.isActive) return@post
                val status = textToSpeech.speak(correction.spokenText, TextToSpeech.QUEUE_FLUSH, null, id)
                if (status == TextToSpeech.ERROR) {
                    pending.remove(id)?.continuation?.resume(Result.failure(IllegalStateException("TTS recusou a fala")))
                }
            }
            continuation.invokeOnCancellation {
                pending.remove(id)
                mainHandler.post { textToSpeech.stop() }
            }
        }
    }

    private fun elapsedMs(startedNanos: Long): Long =
        ((System.nanoTime() - startedNanos) / 1_000_000).coerceAtLeast(0)

    private fun emitTiming(stage: AudioTimingStage, durationMs: Long) {
        runCatching { timingSink.onStage(stage, durationMs) }
    }

    private suspend fun playSignal(direction: SignalDirection): Result<ClockReading> =
        suspendCancellableCoroutine { continuation ->
            val requestedAt = System.nanoTime()
            mainHandler.post {
                if (!continuation.isActive) return@post
                try {
                    val track = signalTracks.getOrPut(direction) { SignalTone.create(direction) }
                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
                    track.reloadStaticData()
                    track.play()
                    val startedAt = clock.now()
                    emitTiming(AudioTimingStage.SIGNAL_REQUEST_TO_START, elapsedMs(requestedAt))
                    mainHandler.postDelayed({ runCatching { if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop() } }, SignalTone.DURATION_MS)
                    continuation.resume(Result.success(startedAt))
                } catch (_: IllegalStateException) {
                    continuation.resume(Result.failure(IllegalStateException("Tom local indisponível")))
                }
            }
        }
}

/**
 * Directional corrections remain stereo. Session transitions use distinct frequencies so the
 * creator can distinguish start, pause, stop, loss, and recovery without looking at the phone.
 */
private enum class SignalDirection {
    LEFT,
    RIGHT,
    CENTER,
    SESSION_STARTED,
    SESSION_PAUSED,
    SESSION_STOPPED,
    SESSION_RESUMED,
    ALERT,
    RECOVERED,
}

private object SignalTone {
    const val DURATION_MS: Long = 80
    private const val SAMPLE_RATE = 48_000

    fun directionFor(signal: SessionSignal): SignalDirection = when (signal) {
        SessionSignal.SESSION_STARTED -> SignalDirection.SESSION_STARTED
        SessionSignal.SESSION_PAUSED -> SignalDirection.SESSION_PAUSED
        SessionSignal.SESSION_STOPPED -> SignalDirection.SESSION_STOPPED
        SessionSignal.SESSION_RESUMED -> SignalDirection.SESSION_RESUMED
        SessionSignal.VOICE_COMMAND_ERROR, SessionSignal.VOICE_INPUT_UNAVAILABLE,
        SessionSignal.CONNECTION_LOST, SessionSignal.ANALYSIS_UNAVAILABLE, SessionSignal.AUDIO_UNAVAILABLE -> SignalDirection.ALERT
        SessionSignal.CONNECTION_RESTORED, SessionSignal.ANALYSIS_RESTORED, SessionSignal.AUDIO_RESTORED -> SignalDirection.RECOVERED
    }

    fun directionFor(direction: Direction): SignalDirection =
        when (direction) {
            Direction.TURN_LEFT, Direction.LEVEL_HORIZON_LEFT -> SignalDirection.LEFT
            Direction.TURN_RIGHT, Direction.LEVEL_HORIZON_RIGHT -> SignalDirection.RIGHT
            else -> SignalDirection.CENTER
        }

    fun create(direction: Direction): AudioTrack = create(directionFor(direction))

    fun create(direction: SignalDirection): AudioTrack {
        val samplesPerChannel = (SAMPLE_RATE * DURATION_MS / 1_000).toInt()
        val pcm = ShortArray(samplesPerChannel * 2)
        val frequency = when (direction) {
            SignalDirection.SESSION_STARTED -> 520.0
            SignalDirection.SESSION_PAUSED -> 350.0
            SignalDirection.SESSION_STOPPED -> 280.0
            SignalDirection.SESSION_RESUMED -> 600.0
            SignalDirection.ALERT -> 660.0
            SignalDirection.RECOVERED -> 880.0
            SignalDirection.LEFT, SignalDirection.RIGHT, SignalDirection.CENTER -> 440.0
        }
        repeat(samplesPerChannel) { index ->
            val sample = (kotlin.math.sin(2.0 * Math.PI * frequency * index / SAMPLE_RATE) * Short.MAX_VALUE * 0.18).toInt().toShort()
            val left = direction != SignalDirection.RIGHT
            val right = direction != SignalDirection.LEFT
            pcm[index * 2] = if (left) sample else 0
            pcm[index * 2 + 1] = if (right) sample else 0
        }
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build(),
            )
            .setBufferSizeInBytes(pcm.size * Short.SIZE_BYTES)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
            .also { track -> track.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING) }
    }
}
