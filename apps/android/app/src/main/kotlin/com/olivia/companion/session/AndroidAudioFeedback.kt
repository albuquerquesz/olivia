package com.olivia.companion.session

import android.content.Context
import android.content.res.Resources
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import com.olivia.core.AudioFeedback
import com.olivia.core.AudioMode
import com.olivia.core.Correction
import com.olivia.core.Direction
import com.olivia.core.SessionSignal
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

enum class AudioFeedbackStatus {
    INITIALIZING,
    READY,
    ERROR,
}

/**
 * Saída local para o headset Bluetooth ou os alto-falantes dos óculos.
 *
 * Voz usa TTS do Android. Sinal usa somente WAVs empacotados no app, com duração curta
 * e volume estéreo para distinguir correções laterais. Nenhuma dessas intenções conhece
 * um modelo específico de óculos.
 */
class AndroidAudioFeedback(
    context: Context,
    private val modeProvider: () -> AudioMode,
) : AudioFeedback {
    private val appContext = context.applicationContext
    private val audioMutex = Mutex()
    private var textToSpeech: TextToSpeech? = null

    @Volatile
    private var textToSpeechReady = false

    private val _status = MutableStateFlow(AudioFeedbackStatus.INITIALIZING)
    private val _statusMessage = MutableStateFlow<String?>(null)
    val status: StateFlow<AudioFeedbackStatus> = _status.asStateFlow()
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    @Volatile
    private var activeTrack: AudioTrack? = null

    init {
        textToSpeech = runCatching {
            TextToSpeech(appContext) { status ->
                if (status != TextToSpeech.SUCCESS) {
                    markError("O TTS do Android não está disponível.")
                } else {
                    val languageStatus = textToSpeech?.setLanguage(Locale("pt", "BR"))
                    if (
                        languageStatus == null ||
                        languageStatus == TextToSpeech.LANG_MISSING_DATA ||
                        languageStatus == TextToSpeech.LANG_NOT_SUPPORTED
                    ) {
                        markError("O pacote de voz em português não está disponível.")
                    } else {
                        textToSpeechReady = true
                        _status.value = AudioFeedbackStatus.READY
                        _statusMessage.value = null
                    }
                }
            }
        }.getOrElse {
            markError("Não foi possível iniciar o TTS do Android.")
            null
        }
    }

    override val mode: AudioMode
        get() = modeProvider()

    override suspend fun announce(correction: Correction) {
        audioMutex.withLock {
            if (mode == AudioMode.VOICE && textToSpeechReady) {
                speakLocked(correction.spokenText, TextToSpeech.QUEUE_ADD)
            } else {
                playCueLocked(cueFor(correction.direction), correction.direction)
            }
        }
    }

    override suspend fun signal(signal: SessionSignal) {
        audioMutex.withLock {
            if (mode == AudioMode.VOICE && textToSpeechReady) {
                // Sinais de estado têm prioridade e limpam uma instrução antiga. Uma nova
                // correção usa QUEUE_ADD, portanto não corta queda ou retomada crítica.
                speakLocked(spokenTextFor(signal), TextToSpeech.QUEUE_FLUSH)
            } else {
                playCueLocked(cueFor(signal))
            }
        }
    }

    override suspend fun stop() {
        activeTrack?.let { track ->
            runCatching {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
            }
        }
        withContext(Dispatchers.Main.immediate) {
            textToSpeech?.stop()
        }
    }

    fun close() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        textToSpeechReady = false
        activeTrack?.let { track ->
            runCatching {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
                track.release()
            }
        }
        activeTrack = null
    }

    private fun speakLocked(text: String, queueMode: Int) {
        if (text.isBlank()) return
        val accepted = textToSpeech?.speak(
            text,
            queueMode,
            null,
            "olivia-${System.nanoTime()}",
        )
        if (accepted == TextToSpeech.ERROR) {
            textToSpeechReady = false
            markError("O TTS do Android recusou a instrução.")
        }
    }

    private suspend fun playCueLocked(cue: AudioCue, direction: Direction? = null) {
        withContext(Dispatchers.Default) {
            try {
                val wave = readWave(appContext.resources, cue.resourceId)
                val channelMask = if (wave.channels == 1) {
                    AudioFormat.CHANNEL_OUT_MONO
                } else {
                    AudioFormat.CHANNEL_OUT_STEREO
                }
                val format = AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(wave.sampleRate)
                    .setChannelMask(channelMask)
                    .build()
                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                val track = AudioTrack.Builder()
                    .setAudioAttributes(attributes)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(wave.pcm.size)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                try {
                    activeTrack = track
                    val pan = direction?.let(::panFor) ?: 0f
                    track.setStereoVolume(channelVolume(pan, left = true), channelVolume(pan, left = false))
                    track.write(wave.pcm, 0, wave.pcm.size, AudioTrack.WRITE_BLOCKING)
                    track.play()
                    delay(wave.durationMs)
                } finally {
                    runCatching {
                        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
                        track.release()
                    }
                    activeTrack = null
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                markError("O sinal sonoro local não pôde ser reproduzido.")
            }
        }
    }

    private fun panFor(direction: Direction): Float = when (cueFor(direction)) {
        AudioCue.DIRECTION_LEFT -> -0.85f
        AudioCue.DIRECTION_RIGHT -> 0.85f
        else -> 0f
    }

    private fun channelVolume(pan: Float, left: Boolean): Float {
        if (pan == 0f) return 1f
        if (left == (pan < 0f)) return 1f
        return 0.16f
    }

    private fun markError(message: String) {
        _status.value = AudioFeedbackStatus.ERROR
        _statusMessage.value = message
    }

    private data class Wave(
        val sampleRate: Int,
        val channels: Int,
        val pcm: ByteArray,
        val durationMs: Long,
    )

    private fun readWave(resources: Resources, resourceId: Int): Wave {
        val bytes = resources.openRawResource(resourceId).use { it.readBytes() }
        require(bytes.size >= 44) { "Cue WAV inválido" }
        require(bytes.copyOfRange(0, 4).contentEquals(byteArrayOf('R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte()))) {
            "Cue WAV sem RIFF"
        }
        require(bytes.copyOfRange(8, 12).contentEquals(byteArrayOf('W'.code.toByte(), 'A'.code.toByte(), 'V'.code.toByte(), 'E'.code.toByte()))) {
            "Cue WAV sem WAVE"
        }

        var channels = 0
        var sampleRate = 0
        var bitsPerSample = 0
        var dataOffset = -1
        var dataSize = 0
        var offset = 12
        while (offset + 8 <= bytes.size) {
            val chunkId = bytes.copyOfRange(offset, offset + 4)
            val chunkSize = littleEndianInt(bytes, offset + 4)
            val payload = offset + 8
            if (chunkId.contentEquals(byteArrayOf('f'.code.toByte(), 'm'.code.toByte(), 't'.code.toByte(), ' '.code.toByte()))) {
                val audioFormat = littleEndianShort(bytes, payload)
                require(audioFormat == 1) { "Cue WAV precisa ser PCM" }
                channels = littleEndianShort(bytes, payload + 2)
                sampleRate = littleEndianInt(bytes, payload + 4)
                bitsPerSample = littleEndianShort(bytes, payload + 14)
            } else if (chunkId.contentEquals(byteArrayOf('d'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte()))) {
                dataOffset = payload
                dataSize = chunkSize.coerceAtMost(bytes.size - payload)
                break
            }
            offset = payload + chunkSize + (chunkSize and 1)
        }
        require(channels > 0 && sampleRate > 0 && bitsPerSample == 16 && dataOffset >= 0) {
            "Cue WAV sem PCM 16-bit estéreo válido"
        }
        val pcm = bytes.copyOfRange(dataOffset, dataOffset + dataSize)
        val bytesPerFrame = channels * 2
        val durationMs = (pcm.size.toLong() * 1_000L / bytesPerFrame / sampleRate).coerceIn(1L, 120L)
        return Wave(sampleRate, channels, pcm, durationMs)
    }

    private fun littleEndianShort(bytes: ByteArray, offset: Int): Int =
        ByteBuffer.wrap(bytes, offset, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xffff

    private fun littleEndianInt(bytes: ByteArray, offset: Int): Int =
        ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int
}
