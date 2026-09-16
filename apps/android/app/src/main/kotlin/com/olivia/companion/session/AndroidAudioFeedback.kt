package com.olivia.companion.session

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import com.olivia.core.AudioFeedback
import com.olivia.core.AudioMode
import com.olivia.core.Correction
import com.olivia.core.Direction
import com.olivia.core.SessionSignal
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Saída local de áudio para o headset Bluetooth ou os alto-falantes dos óculos.
 *
 * Voz usa o TTS do Android. Sinal usa PCM curto gerado localmente: não há round-trip
 * de nuvem e a distinção esquerda/direita é feita pelo volume estéreo.
 */
class AndroidAudioFeedback(
    context: Context,
    private val modeProvider: () -> AudioMode,
) : AudioFeedback {
    private val audioMutex = Mutex()
    private var textToSpeech: TextToSpeech? = null
    private var textToSpeechReady = false

    @Volatile
    private var activeTrack: AudioTrack? = null

    init {
        textToSpeech = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeechReady = textToSpeech?.setLanguage(Locale("pt", "BR"))
                    ?.let { it != TextToSpeech.LANG_MISSING_DATA && it != TextToSpeech.LANG_NOT_SUPPORTED }
                    ?: false
            }
        }
    }

    override val mode: AudioMode
        get() = modeProvider()

    override suspend fun announce(correction: Correction) {
        if (mode == AudioMode.VOICE) {
            speak(correction.spokenText)
            return
        }
        playTone(toneFor(correction.direction))
    }

    override suspend fun signal(signal: SessionSignal) {
        if (mode == AudioMode.VOICE) {
            speak(signalText(signal))
            return
        }
        playTone(toneFor(signal))
    }

    override suspend fun stop() {
        withContext(Dispatchers.Main.immediate) {
            textToSpeech?.stop()
        }
        activeTrack?.let { track ->
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.stop()
            }
        }
    }

    fun close() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        activeTrack?.let { track ->
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.stop()
            }
            track.release()
        }
        activeTrack = null
    }

    private suspend fun speak(text: String) {
        if (text.isBlank()) return
        withContext(Dispatchers.Main.immediate) {
            if (!textToSpeechReady) return@withContext
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "olivia-${System.nanoTime()}")
        }
    }

    private suspend fun playTone(tone: Tone) = audioMutex.withLock {
        withContext(Dispatchers.Default) {
            val frameCount = SAMPLE_RATE * SIGNAL_DURATION_MS / 1_000
            val samples = ShortArray(frameCount * CHANNELS) { index ->
                val frameIndex = index / CHANNELS
                val envelope = envelope(frameIndex, frameCount)
                val sample = sin(2.0 * PI * tone.frequencyHz * frameIndex / SAMPLE_RATE)
                (sample * envelope * Short.MAX_VALUE).toInt().toShort()
            }
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build()
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val minBufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            val track = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(maxOf(minBufferSize, samples.size * 2))
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            try {
                activeTrack = track
                track.setStereoVolume(channelVolume(tone.pan, true), channelVolume(tone.pan, false))
                track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                track.play()
                delay(SIGNAL_DURATION_MS.toLong())
            } finally {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.stop()
                }
                track.release()
                activeTrack = null
            }
        }
    }

    private fun envelope(index: Int, sampleCount: Int): Double {
        val fadeSamples = SAMPLE_RATE * 8 / 1_000
        if (index < fadeSamples) return index.toDouble() / fadeSamples
        if (index >= sampleCount - fadeSamples) return (sampleCount - index).toDouble() / fadeSamples
        return 0.42
    }

    private fun channelVolume(pan: Float, leftChannel: Boolean): Float {
        if (pan == 0f) return 1f
        if (leftChannel == (pan < 0f)) return 1f
        return 0.16f
    }

    private fun toneFor(direction: Direction): Tone = when (direction) {
        Direction.TURN_LEFT,
        Direction.LEVEL_HORIZON_LEFT -> Tone(frequencyHz = 520.0, pan = -0.85f)
        Direction.TURN_RIGHT,
        Direction.LEVEL_HORIZON_RIGHT -> Tone(frequencyHz = 520.0, pan = 0.85f)
        Direction.MOVE_CLOSER,
        Direction.MOVE_BACK,
        Direction.RAISE,
        Direction.LOWER,
        Direction.FACE_THE_LIGHT,
        Direction.AVOID_BACKLIGHT,
        Direction.LEAVE_THE_SHADOW -> Tone(frequencyHz = 680.0, pan = 0f)
    }

    private fun toneFor(signal: SessionSignal): Tone = when (signal) {
        SessionSignal.SESSION_STARTED -> Tone(frequencyHz = 880.0, pan = 0f)
        SessionSignal.SESSION_PAUSED -> Tone(frequencyHz = 560.0, pan = 0f)
        SessionSignal.SESSION_RESUMED -> Tone(frequencyHz = 760.0, pan = 0f)
        SessionSignal.SESSION_STOPPED -> Tone(frequencyHz = 440.0, pan = 0f)
        SessionSignal.CONNECTION_LOST -> Tone(frequencyHz = 220.0, pan = 0f)
        SessionSignal.CONNECTION_RESTORED -> Tone(frequencyHz = 660.0, pan = 0f)
        SessionSignal.VOICE_COMMAND_ERROR -> Tone(frequencyHz = 300.0, pan = 0f)
        SessionSignal.VOICE_INPUT_UNAVAILABLE -> Tone(frequencyHz = 180.0, pan = 0f)
    }

    private fun signalText(signal: SessionSignal): String = when (signal) {
        SessionSignal.SESSION_STARTED -> "Sessão iniciada"
        SessionSignal.SESSION_PAUSED -> "Sessão pausada"
        SessionSignal.SESSION_RESUMED -> "Sessão retomada"
        SessionSignal.SESSION_STOPPED -> "Sessão encerrada"
        SessionSignal.CONNECTION_LOST -> "Conexão perdida"
        SessionSignal.CONNECTION_RESTORED -> "Conexão restaurada"
        SessionSignal.VOICE_COMMAND_ERROR -> "Não entendi o comando"
        SessionSignal.VOICE_INPUT_UNAVAILABLE -> "Comandos de voz indisponíveis"
    }

    private data class Tone(
        val frequencyHz: Double,
        val pan: Float,
    )

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val SIGNAL_DURATION_MS = 80
        const val CHANNELS = 2
    }
}
