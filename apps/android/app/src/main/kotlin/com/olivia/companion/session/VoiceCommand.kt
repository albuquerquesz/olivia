package com.olivia.companion.session

import java.text.Normalizer
import java.util.Locale

/** Comandos de voz que podem controlar uma sessão. A lista é deliberadamente fechada. */
enum class VoiceCommand {
    START,
    PAUSE,
    RESUME,
    STOP,
}

/**
 * Converte apenas os comandos permitidos em uma intenção de sessão.
 *
 * O texto recebido é transitório: a função devolve o enum e não guarda a fala.
 */
internal fun parseVoiceCommand(transcript: String): VoiceCommand? {
    val words = Normalizer.normalize(transcript.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
        .split("[^a-z0-9]+".toRegex())
        .filter(String::isNotBlank)

    if (words.isEmpty()) return null

    val commandWords = mapOf(
        "gravar" to VoiceCommand.START,
        "pausar" to VoiceCommand.PAUSE,
        "retomar" to VoiceCommand.RESUME,
        "terminar" to VoiceCommand.STOP,
    )
    val commandWord = words.lastOrNull { it in commandWords } ?: return null
    val allowedContext = setOf("oi", "olivia", "por", "favor")
    if (words.any { it != commandWord && it !in allowedContext }) return null
    return commandWords[commandWord]
}
