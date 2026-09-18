package com.olivia.companion.session

import ai.picovoice.porcupine.PorcupineException
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import ai.picovoice.porcupine.PorcupineManagerErrorCallback
import ai.picovoice.rhino.RhinoInference
import ai.picovoice.rhino.RhinoManager
import ai.picovoice.rhino.RhinoManagerCallback
import ai.picovoice.rhino.RhinoManagerErrorCallback
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.olivia.companion.BuildConfig
import java.io.File

/** Adapter Android do wake word Porcupine, com o callback isolado da sessao. */
internal class PorcupineWakeWordDetector(
    context: Context,
    accessKey: String,
    keywordPath: String,
    modelPath: String,
    private val onWakeWord: () -> Unit,
    private val onError: () -> Unit,
) : WakeWordDetector {
    private val activationCue = VoiceActivationCue()
    private val manager = PorcupineManager.Builder()
        .setAccessKey(accessKey)
        .setModelPath(modelPath)
        .setKeywordPath(keywordPath)
        .setSensitivity(0.6f)
        .setErrorCallback(object : PorcupineManagerErrorCallback {
            override fun invoke(error: PorcupineException) {
                Log.w(TAG, "Porcupine audio error: ${error::class.java.simpleName}")
                onError()
            }
        })
        .build(
            context.applicationContext,
            object : PorcupineManagerCallback {
                override fun invoke(keywordIndex: Int) {
                    // Only the configured keyword index is logged; never log audio.
                    Log.i(TAG, "Wake word detected: keyword=$keywordIndex")
                    activationCue.play()
                    onWakeWord()
                }
            },
        )

    private var started = false

    override fun start() {
        if (started) return
        runCatching { manager.start() }
            .onSuccess {
                started = true
                Log.i(TAG, "Porcupine listening")
            }
            .onFailure { error ->
                Log.w(TAG, "Porcupine start failed: ${error::class.java.simpleName}")
                throw error
            }
    }

    override fun stop() {
        if (!started) return
        manager.stop()
        started = false
        Log.i(TAG, "Porcupine stopped")
    }

    override fun close() {
        runCatching { stop() }
        manager.delete()
        activationCue.close()
    }

    private companion object {
        const val TAG = "OliviaVoice"
    }
}

/** Short local acknowledgement played when Rhino's command window opens. */
private class VoiceActivationCue : AutoCloseable {
    private val handler = Handler(Looper.getMainLooper())
    private val track: AudioTrack? = runCatching { createTrack() }
        .onFailure { error ->
            Log.w(TAG, "Voice activation cue unavailable: ${error::class.java.simpleName}")
        }
        .getOrNull()

    fun play() {
        handler.post {
            val audio = track ?: return@post
            runCatching {
                if (audio.playState == AudioTrack.PLAYSTATE_PLAYING) audio.stop()
                audio.reloadStaticData()
                audio.play()
                handler.postDelayed(
                    { runCatching { if (audio.playState == AudioTrack.PLAYSTATE_PLAYING) audio.stop() } },
                    DURATION_MS,
                )
            }.onFailure { error ->
                Log.w(TAG, "Voice activation cue failed: ${error::class.java.simpleName}")
            }
        }
    }

    override fun close() {
        handler.post {
            track?.let { audio ->
                runCatching {
                    if (audio.playState == AudioTrack.PLAYSTATE_PLAYING) audio.stop()
                    audio.release()
                }
            }
        }
    }

    private fun createTrack(): AudioTrack {
        val sampleCount = (SAMPLE_RATE * DURATION_MS / 1_000).toInt()
        val pcm = ShortArray(sampleCount * 2)
        repeat(sampleCount) { index ->
            val envelope = (1f - index.toFloat() / sampleCount).coerceAtLeast(0f)
            val sample = (
                kotlin.math.sin(2.0 * Math.PI * FREQUENCY_HZ * index / SAMPLE_RATE) *
                    Short.MAX_VALUE * 0.16 * envelope
                ).toInt().toShort()
            pcm[index * 2] = sample
            pcm[index * 2 + 1] = sample
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
            .also { audio -> audio.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING) }
    }

    private companion object {
        const val TAG = "OliviaVoice"
        const val DURATION_MS = 90L
        const val SAMPLE_RATE = 48_000
        const val FREQUENCY_HZ = 880.0
    }
}

/** Reconhece uma janela curta de fala com Rhino, integralmente no dispositivo. */
internal class RhinoCommandRecognizer(
    private val context: Context,
    private val accessKey: String,
    private val contextPath: String,
    private val modelPath: String,
    private val onCandidates: (List<String>) -> Unit,
    private val onError: () -> Unit,
) : LocalCommandRecognizer {
    private var manager: RhinoManager? = null
    private var closed = false

    override fun start() {
        if (closed || manager != null) return
        val created = runCatching {
            RhinoManager.Builder()
                .setAccessKey(accessKey)
                .setContextPath(contextPath)
                .setModelPath(modelPath)
                .setSensitivity(RHINO_SENSITIVITY)
                .setEndpointDurationSec(RHINO_ENDPOINT_SECONDS)
                .setRequireEndpoint(true)
                .setErrorCallback(RhinoManagerErrorCallback { error ->
                    Log.w(TAG, "Rhino command failed: ${error::class.java.simpleName}")
                    onError()
                })
                .build(context.applicationContext, RhinoManagerCallback { inference ->
                    onInference(inference)
                })
        }.getOrElse { error ->
            Log.w(TAG, "Rhino initialization failed: ${error::class.java.simpleName}")
            onError()
            return
        }

        manager = created
        Log.i(TAG, "Rhino listening for closed intent")
        runCatching { created.process() }.onFailure { error ->
            Log.w(TAG, "Rhino capture failed: ${error::class.java.simpleName}")
            stop()
            onError()
        }
    }

    override fun stop() {
        if (manager != null) Log.i(TAG, "Rhino stopped")
        manager?.delete()
        manager = null
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { stop() }
    }

    private fun onInference(inference: RhinoInference) {
        if (closed) return
        // Only the structured intent crosses this boundary. Rhino never exposes a transcript
        // to the session and the command parser accepts this closed vocabulary.
        if (inference.isUnderstood) {
            val intent = inference.intent.orEmpty()
            Log.i(TAG, "Rhino intent understood: ${intent.ifBlank { "<empty>" }}")
            if (intent.isNotBlank()) onCandidates(listOf(intent)) else onCandidates(emptyList())
        } else {
            Log.i(TAG, "Rhino intent not understood")
            onCandidates(emptyList())
        }
    }

    private companion object {
        const val TAG = "OliviaVoice"
        const val RHINO_SENSITIVITY = 0.45f
        // Keep enough silence after the command for the short "gravar" utterance
        // without ending the Rhino window while the user is still speaking.
        const val RHINO_ENDPOINT_SECONDS = 1.0f
    }
}

object AndroidVoiceCommandSourceFactory {
    fun create(context: Context): VoiceCommandSource {
        val accessKey = BuildConfig.PICOVOICE_ACCESS_KEY
        val keywordPath = BuildConfig.PICOVOICE_KEYWORD_PATH
        val porcupineModelPath = BuildConfig.PICOVOICE_PORCUPINE_MODEL_PATH
        val contextPath = BuildConfig.PICOVOICE_RHINO_CONTEXT_PATH
        val modelPath = BuildConfig.PICOVOICE_RHINO_MODEL_PATH
        if (
            accessKey.isBlank() ||
            !assetExists(context, keywordPath) ||
            !assetExists(context, porcupineModelPath) ||
            !assetExists(context, contextPath) ||
            !assetExists(context, modelPath)
        ) {
            Log.i(TAG, "Voice unavailable: missing access key or local Picovoice asset")
            return UnavailableVoiceCommandSource()
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Voice unavailable: microphone permission not granted")
            return UnavailableVoiceCommandSource()
        }

        lateinit var source: LocalVoiceCommandSource
        var wakeWordDetector: WakeWordDetector? = null
        var commandRecognizer: LocalCommandRecognizer? = null
        return try {
            val detector = PorcupineWakeWordDetector(
                context = context,
                accessKey = accessKey,
                keywordPath = keywordPath,
                modelPath = porcupineModelPath,
                onWakeWord = { source.onWakeWordDetected() },
                onError = { source.onWakeWordError() },
            )
            wakeWordDetector = detector
            val recognizer = RhinoCommandRecognizer(
                context = context,
                accessKey = accessKey,
                contextPath = contextPath,
                modelPath = modelPath,
                onCandidates = { candidates -> source.onCommandCandidates(candidates) },
                onError = { source.onCommandError() },
            )
            commandRecognizer = recognizer
            source = LocalVoiceCommandSource(detector, recognizer)
            Log.i(TAG, "Voice local ready: Porcupine wake word + Rhino intents")
            source
        } catch (error: Throwable) {
            runCatching { commandRecognizer?.close() }
            runCatching { wakeWordDetector?.close() }
            Log.w(TAG, "Voice initialization failed: ${error::class.java.simpleName}")
            UnavailableVoiceCommandSource()
        }
    }

    private fun assetExists(context: Context, path: String): Boolean {
        if (path.isBlank()) return false
        if (path.startsWith('/')) return File(path).isFile
        return runCatching { context.assets.open(path).use { true } }.getOrDefault(false)
    }

    private const val TAG = "OliviaVoice"
}
