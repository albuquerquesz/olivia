package com.olivia.companion.transcription

import android.content.Context
import android.util.Log
import com.olivia.core.LocalTranscriptProvider
import com.olivia.core.RecordingAsset
import com.olivia.core.TranscriptSegment
import com.whispercpp.whisper.WhisperContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Real local Whisper transcription (BE-12/BE-08): runs only on an already-imported
 * post-STOPPED file, forced to pt-BR (see jni.c), on the vendored whisper.cpp build.
 * No network call, no live microphone — see third_party/whisper-cpp/NOTICE.md.
 */
class WhisperLocalTranscriptProvider(
    private val context: Context,
    private val resolveLocalPath: suspend (RecordingAsset) -> String?,
    private val modelAssetPath: String = "whisper/ggml-base-q5_1.bin",
) : LocalTranscriptProvider {

    override suspend fun transcribe(recording: RecordingAsset): Result<List<TranscriptSegment>> =
        withContext(Dispatchers.Default) {
            runCatching {
                if (!recording.hasAudioTrack) return@runCatching emptyList()
                val path = resolveLocalPath(recording)
                    ?: throw IllegalStateException("Arquivo local da gravação indisponível para transcrição")

                val decodeStart = System.currentTimeMillis()
                val pcm = AudioDecoder.decodeToMonoPcm16k(path)
                val decodeMs = System.currentTimeMillis() - decodeStart

                val loadStart = System.currentTimeMillis()
                val whisperContext = WhisperContext.createContextFromAsset(context.assets, modelAssetPath)
                val loadMs = System.currentTimeMillis() - loadStart
                try {
                    val inferStart = System.currentTimeMillis()
                    val raw = whisperContext.transcribeData(pcm, printTimestamp = true)
                    val inferMs = System.currentTimeMillis() - inferStart
                    Log.i(
                        LOG_TAG,
                        "audioMs=${pcm.size * 1000L / 16_000} decodeMs=$decodeMs modelLoadMs=$loadMs inferMs=$inferMs",
                    )
                    parseSegments(raw)
                } finally {
                    whisperContext.release()
                }
            }
        }

    /** Matches WhisperContext.transcribeData's own "[HH:MM:SS.mmm --> HH:MM:SS.mmm]: text" format. */
    private fun parseSegments(raw: String): List<TranscriptSegment> {
        val pattern = TIMESTAMPED_LINE
        return raw.lineSequence()
            .mapNotNull { line -> pattern.matchEntire(line.trim()) }
            .mapNotNull { match ->
                val groups = match.groupValues
                val text = groups[9].trim()
                if (text.isEmpty()) return@mapNotNull null
                TranscriptSegment(
                    startMs = toMillis(groups[1], groups[2], groups[3], groups[4]),
                    endMs = toMillis(groups[5], groups[6], groups[7], groups[8]),
                    text = text,
                )
            }
            .toList()
    }

    private fun toMillis(hours: String, minutes: String, seconds: String, millis: String): Long =
        hours.toLong() * 3_600_000L +
            minutes.toLong() * 60_000L +
            seconds.toLong() * 1_000L +
            millis.toLong()

    private companion object {
        private const val LOG_TAG = "WhisperTranscript"
        val TIMESTAMPED_LINE = Regex(
            """\[(\d{2}):(\d{2}):(\d{2})\.(\d{3}) --> (\d{2}):(\d{2}):(\d{2})\.(\d{3})]:\s*(.*)""",
        )
    }
}
