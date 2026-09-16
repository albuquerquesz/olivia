package com.olivia.companion.session

import ai.picovoice.porcupine.PorcupineException
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import ai.picovoice.porcupine.PorcupineManagerErrorCallback
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.io.File

/** Adapter Android do wake word Porcupine, com o callback isolado da sessão. */
internal class PorcupineWakeWordDetector(
    context: Context,
    accessKey: String,
    keywordPath: String,
    private val onWakeWord: () -> Unit,
    private val onError: () -> Unit,
) : WakeWordDetector {
    private val manager = PorcupineManager.Builder()
        .setAccessKey(accessKey)
        .setKeywordPath(keywordPath)
        .setSensitivity(0.6f)
        .setErrorCallback(object : PorcupineManagerErrorCallback {
            override fun invoke(error: PorcupineException) {
                onError()
            }
        })
        .build(
            context.applicationContext,
            object : PorcupineManagerCallback {
                override fun invoke(keywordIndex: Int) {
                    onWakeWord()
                }
            },
        )

    private var started = false

    override fun start() {
        if (started) return
        manager.start()
        started = true
    }

    override fun stop() {
        if (!started) return
        manager.stop()
        started = false
    }

    override fun close() {
        runCatching { stop() }
        manager.delete()
    }
}

/** Reconhece uma janela de fala curta somente pelo serviço on-device do Android. */
internal class AndroidOnDeviceCommandRecognizer(
    context: Context,
    private val onCandidates: (List<String>) -> Unit,
    private val onError: () -> Unit,
) : LocalCommandRecognizer {
    private val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context.applicationContext)
    private val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pt-BR")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        // Defesa adicional caso o provider tente escolher a implementação online.
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
    }

    init {
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit

            override fun onBeginningOfSpeech() = Unit

            override fun onRmsChanged(rmsdB: Float) = Unit

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() = Unit

            override fun onError(error: Int) {
                onError()
            }

            override fun onResults(results: Bundle?) {
                val candidates = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    .orEmpty()
                onCandidates(candidates)
            }

            override fun onPartialResults(partialResults: Bundle?) = Unit

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
    }

    override fun start() {
        recognizer.startListening(intent)
    }

    override fun stop() {
        recognizer.stopListening()
        recognizer.cancel()
    }

    override fun close() {
        runCatching { stop() }
        recognizer.destroy()
    }
}

object AndroidVoiceCommandSourceFactory {
    fun create(context: Context): VoiceCommandSource {
        val accessKey = BuildConfig.PICOVOICE_ACCESS_KEY
        val keywordPath = BuildConfig.PICOVOICE_KEYWORD_PATH
        if (accessKey.isBlank() || !keywordExists(context, keywordPath)) {
            return UnavailableVoiceCommandSource()
        }
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            !SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        ) {
            return UnavailableVoiceCommandSource()
        }

        lateinit var source: LocalVoiceCommandSource
        return runCatching {
            val wakeWordDetector = PorcupineWakeWordDetector(
                context = context,
                accessKey = accessKey,
                keywordPath = keywordPath,
                onWakeWord = { source.onWakeWordDetected() },
                onError = { source.onWakeWordError() },
            )
            val commandRecognizer = AndroidOnDeviceCommandRecognizer(
                context = context,
                onCandidates = { candidates -> source.onCommandCandidates(candidates) },
                onError = { source.onCommandError() },
            )
            source = LocalVoiceCommandSource(wakeWordDetector, commandRecognizer)
            source
        }.getOrElse {
            UnavailableVoiceCommandSource()
        }
    }

    private fun keywordExists(context: Context, path: String): Boolean {
        if (path.isBlank()) return false
        if (path.startsWith('/')) return File(path).isFile
        return runCatching { context.assets.open(path).use { true } }.getOrDefault(false)
    }
}
