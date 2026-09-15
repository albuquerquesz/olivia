package com.olivia.companion.media

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.olivia.core.RecordingAsset
import com.olivia.core.RecordingSource
import com.olivia.core.SessionId
import com.olivia.core.SessionSnapshot
import com.olivia.core.SessionState
import com.olivia.core.TransferProgress
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Explicit post-take fallback import for a media item the creator selected on this phone.
 *
 * It accepts only content-provider URIs, validates the container before copying into app-private
 * cache, and never deletes or mutates the original URI. It is not proof of DAT transfer, 3K
 * recording, or Bluetooth-loss continuity.
 */
class AndroidLocalRecordingSource(context: Context) : RecordingSource {
    private val resolver = context.applicationContext.contentResolver
    private val importDirectory = File(context.cacheDir, "olivia-recordings")
    private val imports = mutableMapOf<String, ImportedTake>()
    private val completed = mutableMapOf<String, File>()

    /** Registers a selected local video only after it passes metadata and track validation. */
    suspend fun registerSelectedTake(
        session: SessionSnapshot,
        uri: Uri,
    ): Result<RecordingAsset> = try {
        require(uri.scheme == "content") { "Only local content URIs are accepted" }
        require(session.state == SessionState.STOPPED) { "Recording import requires a stopped session" }
        val metadata = inspect(uri)
        val asset = RecordingAsset(
            id = UUID.randomUUID().toString(),
            sessionId = session.id,
            startedAtEpochMs = session.startedAtEpochMs,
            durationMs = metadata.durationMs,
            sizeBytes = metadata.sizeBytes,
            hasAudioTrack = metadata.hasAudioTrack,
        )
        synchronized(imports) { imports[asset.id] = ImportedTake(asset, uri) }
        Result.success(asset)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        Result.failure(IllegalArgumentException("Selected recording is unavailable or invalid"))
    }

    override suspend fun listTakes(sessionId: SessionId): List<RecordingAsset> =
        synchronized(imports) { imports.values.map(ImportedTake::asset).filter { it.sessionId == sessionId } }

    override fun fetch(asset: RecordingAsset): Flow<TransferProgress> = flow {
        val registered = synchronized(imports) { imports[asset.id] }
            ?: throw IllegalArgumentException("Unknown local recording")
        require(registered.asset == asset) { "Recording metadata mismatch" }

        val destination = File(importDirectory, "${asset.id}.mp4")
        val partial = File(importDirectory, "${asset.id}.part")
        importDirectory.mkdirs()
        partial.delete()
        var copied = 0L
        try {
            emit(TransferProgress(asset.id, copied, asset.sizeBytes))
            resolver.openInputStream(registered.uri)?.use { input ->
                FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        copied += count
                        require(copied <= asset.sizeBytes) { "Selected recording changed during import" }
                        emit(TransferProgress(asset.id, copied, asset.sizeBytes))
                    }
                    output.fd.sync()
                }
            } ?: throw IllegalArgumentException("Cannot read selected recording")
            require(copied == asset.sizeBytes) { "Incomplete selected recording" }
            if (destination.exists()) destination.delete()
            require(partial.renameTo(destination)) { "Cannot finalize local import" }
            synchronized(completed) { completed[asset.id] = destination }
        } catch (error: CancellationException) {
            partial.delete()
            throw error
        } catch (error: Throwable) {
            partial.delete()
            throw IllegalStateException("Local recording import failed")
        }
    }

    override suspend fun localPathOf(assetId: String): String? =
        synchronized(completed) { completed[assetId]?.takeIf(File::isFile)?.absolutePath }

    /** Deletes only Olivia's private copy after post-processing; it never deletes the original. */
    suspend fun discardImportedCopy(assetId: String) {
        val file = synchronized(completed) { completed.remove(assetId) }
        file?.delete()
    }

    private fun inspect(uri: Uri): RecordingMetadata {
        val declaredMime = resolver.getType(uri)?.lowercase()
        require(declaredMime?.startsWith("video/") == true) { "Selected item is not a video" }
        val sizeBytes = resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) cursor.getLong(index) else null
        } ?: throw IllegalArgumentException("Selected video size is unknown")
        require(sizeBytes > 0) { "Selected video is empty" }

        val extractor = MediaExtractor()
        try {
            resolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                extractor.setDataSource(descriptor.fileDescriptor)
            } ?: throw IllegalArgumentException("Cannot open selected video")
            val mimeTypes = (0 until extractor.trackCount).mapNotNull { index ->
                extractor.getTrackFormat(index).getString("mime")
            }
            require(mimeTypes.any { it.startsWith("video/") }) { "Container has no video track" }
            require(mimeTypes.any { it.startsWith("audio/") }) { "Container has no audio track" }
        } finally {
            extractor.release()
        }
        val retriever = MediaMetadataRetriever()
        try {
            resolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                retriever.setDataSource(descriptor.fileDescriptor)
            } ?: throw IllegalArgumentException("Cannot inspect selected video")
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: throw IllegalArgumentException("Selected video duration is unknown")
            require(durationMs > 0) { "Selected video duration is invalid" }
            return RecordingMetadata(sizeBytes, durationMs, hasAudioTrack = true)
        } finally {
            retriever.release()
        }
    }

    private data class ImportedTake(val asset: RecordingAsset, val uri: Uri)
    private data class RecordingMetadata(val sizeBytes: Long, val durationMs: Long, val hasAudioTrack: Boolean)

    private companion object {
        const val COPY_BUFFER_BYTES = 64 * 1024
    }
}
