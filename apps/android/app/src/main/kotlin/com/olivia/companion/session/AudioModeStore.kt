package com.olivia.companion.session

import android.content.Context
import com.olivia.core.AudioMode

/** Porta pequena para que o estado da preferência possa ser exercitado sem Android nos testes. */
interface AudioModeRepository {
    fun current(): AudioMode

    fun set(mode: AudioMode)
}

class AudioModeStore(context: Context) : AudioModeRepository {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun current(): AudioMode {
        val stored = prefs.getString(KEY_MODE, AudioMode.VOICE.name) ?: AudioMode.VOICE.name
        return runCatching { AudioMode.valueOf(stored) }.getOrDefault(AudioMode.VOICE)
    }

    override fun set(mode: AudioMode) {
        prefs.edit().putString(KEY_MODE, mode.name).apply()
    }

    private companion object {
        const val PREFS_NAME = "olivia_settings"
        const val KEY_MODE = "audio_mode"
    }
}
