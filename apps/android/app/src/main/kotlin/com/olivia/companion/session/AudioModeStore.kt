package com.olivia.companion.session

import android.content.Context
import com.olivia.core.AudioMode

class AudioModeStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun current(): AudioMode {
        val stored = prefs.getString(KEY_MODE, AudioMode.VOICE.name) ?: AudioMode.VOICE.name
        return runCatching { AudioMode.valueOf(stored) }.getOrDefault(AudioMode.VOICE)
    }

    fun set(mode: AudioMode) {
        prefs.edit().putString(KEY_MODE, mode.name).apply()
    }

    private companion object {
        const val PREFS_NAME = "olivia_settings"
        const val KEY_MODE = "audio_mode"
    }
}
