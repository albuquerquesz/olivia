package com.olivia.companion.session

import android.content.Context
import com.olivia.core.RecordingAsset
import com.olivia.core.RecordingSource
import com.olivia.core.SessionId
import com.olivia.core.TransferProgress
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * Fonte de gravação exclusiva do flavor mock.
 *
 * O arquivo é uma fixture local, sem frame de análise ou áudio de terceiros. Ela existe apenas
 * para tornar reproduzível o handoff pós-sessão; o flavor dos óculos continua usando o adapter
 * nativo quando BE-12 estiver disponível.
 */
internal class ScenarioRecordingSource(context: Context) : RecordingSource {
    private val appContext = context.applicationContext
    private val recordingDirectory = File(appContext.filesDir, "recordings")

    override suspend fun listTakes(sessionId: SessionId): List<RecordingAsset> {
        val sizeBytes = fixtureSizeBytes() ?: return emptyList()
        return listOf(
            RecordingAsset(
                id = assetId(sessionId),
                sessionId = sessionId,
                startedAtEpochMs = 0L,
                durationMs = SessionScenario.SCENARIO_DURATION_MS,
                sizeBytes = sizeBytes,
                hasAudioTrack = true,
            ),
        )
    }

    override fun fetch(asset: RecordingAsset): Flow<TransferProgress> = flow {
        val target = targetFile(asset.id)
        val totalBytes = asset.sizeBytes.coerceAtLeast(0L)
        emit(TransferProgress(asset.id, 0L, totalBytes))
        if (target.isFile && target.length() == totalBytes) {
            emit(TransferProgress(asset.id, totalBytes, totalBytes))
            return@flow
        }

        withContext(Dispatchers.IO) {
            recordingDirectory.mkdirs()
            val partial = File(target.parentFile ?: recordingDirectory, "${target.name}.part")
            try {
                appContext.assets.open(FIXTURE_NAME).use { input ->
                    partial.outputStream().use { output -> input.copyTo(output) }
                }
                if (target.exists()) target.delete()
                check(partial.renameTo(target)) { "Não foi possível preparar a fixture de vídeo" }
            } finally {
                if (partial.exists()) partial.delete()
            }
        }
        emit(TransferProgress(asset.id, target.length(), totalBytes))
    }

    override suspend fun localPathOf(assetId: String): String? =
        targetFile(assetId).takeIf { it.isFile }?.absolutePath

    private suspend fun fixtureSizeBytes(): Long? = withContext(Dispatchers.IO) {
        runCatching {
            appContext.assets.open(FIXTURE_NAME).use { it.readBytes().size.toLong() }
        }.getOrNull()?.takeIf { it > 0L }
    }

    private fun assetId(sessionId: SessionId): String = "mock-recording-${safeId(sessionId.value)}"

    private fun targetFile(assetId: String): File =
        File(recordingDirectory, "${safeId(assetId)}.mp4")

    private fun safeId(value: String): String =
        value.replace(UNSAFE_ID, "_").ifBlank { "session" }

    private companion object {
        const val FIXTURE_NAME = "olivia_mock_recording.mp4"
        val UNSAFE_ID = Regex("[^A-Za-z0-9._-]")
    }
}
