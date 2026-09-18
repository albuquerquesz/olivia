package com.olivia.companion.session

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.olivia.core.SessionSnapshot
import com.olivia.core.TimelineEvent
import com.olivia.core.TimelineEventType
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Small, device-local index for the Sessions screen.
 *
 * This is deliberately not media storage: only aggregate session metadata is retained. Frames,
 * audio, video URIs and model output never enter this store. DataStore also gives us an atomic
 * update if the process is killed while a take is being closed.
 */
data class SessionHistoryEntry(
    val sessionId: String,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long,
    val diagnosisCount: Int,
    val correctionCount: Int,
)

private val Context.oliviaSessionHistoryDataStore by preferencesDataStore(
    name = "olivia_session_history",
)

class LocalSessionHistory(context: Context) {
    private val appContext = context.applicationContext

    val entries: Flow<List<SessionHistoryEntry>> = appContext.oliviaSessionHistoryDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { values -> decode(values[HISTORY]) }

    suspend fun record(snapshot: SessionSnapshot, timeline: List<TimelineEvent>) {
        val entry = SessionHistoryEntry(
            sessionId = snapshot.id.value,
            startedAtEpochMs = snapshot.startedAtEpochMs,
            endedAtEpochMs = snapshot.startedAtEpochMs + snapshot.elapsedMs,
            diagnosisCount = timeline.count { it.type == TimelineEventType.DIAGNOSIS },
            correctionCount = timeline.count { it.type == TimelineEventType.CORRECTION_ISSUED },
        )
        appContext.oliviaSessionHistoryDataStore.edit { values ->
            val updated = (decode(values[HISTORY])
                .filterNot { it.sessionId == entry.sessionId } + entry)
                .takeLast(MAX_ENTRIES)
            values[HISTORY] = updated.joinToString("\n", transform = ::encode)
        }
    }

    private fun encode(entry: SessionHistoryEntry): String = listOf(
        entry.sessionId,
        entry.startedAtEpochMs,
        entry.endedAtEpochMs,
        entry.diagnosisCount,
        entry.correctionCount,
    ).joinToString("|")

    private fun decode(value: String?): List<SessionHistoryEntry> = value.orEmpty()
        .lineSequence()
        .mapNotNull { line ->
            val fields = line.split('|')
            if (fields.size != 5) return@mapNotNull null
            runCatching {
                SessionHistoryEntry(
                    sessionId = fields[0].takeIf(String::isNotBlank) ?: return@runCatching null,
                    startedAtEpochMs = fields[1].toLong(),
                    endedAtEpochMs = fields[2].toLong(),
                    diagnosisCount = fields[3].toInt().coerceAtLeast(0),
                    correctionCount = fields[4].toInt().coerceAtLeast(0),
                )
            }.getOrNull()
        }
        .toList()

    private companion object {
        val HISTORY = stringPreferencesKey("entries")
        const val MAX_ENTRIES = 20
    }
}
