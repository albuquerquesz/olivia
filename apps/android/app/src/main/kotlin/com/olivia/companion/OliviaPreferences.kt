package com.olivia.companion

import android.content.Context
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.olivia.core.AudioMode
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.oliviaPreferencesDataStore by preferencesDataStore(name = "olivia_preferences")

/**
 * The app's small, device-local preference store. It deliberately contains no identity, media,
 * transcript, session timeline, URI, or credential. The top-level DataStore delegate guarantees
 * a single instance per process.
 */
class OliviaPreferences(context: Context) {
    private val appContext = context.applicationContext

    val audioMode: Flow<AudioMode> = appContext.oliviaPreferencesDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { values -> values[AUDIO_MODE]?.let(::parseAudioMode) ?: AudioMode.VOICE }

    suspend fun setAudioMode(mode: AudioMode) {
        appContext.oliviaPreferencesDataStore.edit { values ->
            values[AUDIO_MODE] = mode.name
        }
    }

    private fun parseAudioMode(value: String): AudioMode =
        AudioMode.entries.firstOrNull { it.name == value } ?: AudioMode.VOICE

    private companion object {
        val AUDIO_MODE = stringPreferencesKey("audio_mode")
    }
}
