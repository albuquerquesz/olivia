package com.olivia.companion.session

import android.content.Context
import android.net.Uri
import com.olivia.companion.media.AndroidLocalRecordingSource
import com.olivia.companion.media.AndroidReviewExporter
import com.olivia.companion.media.LocalReviewExport
import com.olivia.companion.transcription.WhisperLocalTranscriptProvider
import com.olivia.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext

interface PostTakeController {
    val hasDemoRecording: Boolean
    suspend fun prepareDemo(session: SessionSnapshot, events: List<TimelineEvent>): Result<SessionReview>
    suspend fun importRecording(session: SessionSnapshot, uri: String, events: List<TimelineEvent>): Result<SessionReview>
    suspend fun share(review: SessionReview, marksOnly: Boolean): ExportState
    suspend fun discard(review: SessionReview)
}

/** Reuses the backend's validated local import and export; never touches live frames. */
class AndroidPostTakeController(context: Context, private val demoSource: RecordingSource?) : PostTakeController {
    private val appContext = context.applicationContext
    private val importer = AndroidLocalRecordingSource(context)
    private val exporter = AndroidReviewExporter(context)
    private val combinedExporter = AndroidSessionExportAdapter(context.applicationContext)
    private val exports = mutableMapOf<SessionId, LocalReviewExport>()
    private val importedSessions = mutableSetOf<SessionId>()
    override val hasDemoRecording: Boolean get() = demoSource != null

    override suspend fun prepareDemo(session: SessionSnapshot, events: List<TimelineEvent>): Result<SessionReview> {
        val source = requireNotNull(demoSource)
        val asset = source.listTakes(session.id).firstOrNull()
            ?: return Result.failure(IllegalStateException("Fixture indisponível"))
        source.fetch(asset).collect { }
        // Packaged demo clip, not the recording of the live mock session.
        return TimelinePostProcessor().process(session.id, asset, events)
    }

    override suspend fun importRecording(session: SessionSnapshot, uri: String, events: List<TimelineEvent>): Result<SessionReview> = withContext(Dispatchers.IO) {
        val asset = importer.registerSelectedTake(session, Uri.parse(uri)).getOrElse { return@withContext Result.failure(it) }
        try {
            importer.fetch(asset).collect { }
            LocalPostProcessor(
                transcriptProvider = WhisperLocalTranscriptProvider(
                    context = appContext,
                    resolveLocalPath = { recording -> importer.localPathOf(recording.id) },
                ),
                alignmentFor = { recording -> LinearMediaAlignment(session.id, recording.startedAtEpochMs, 0) },
            ).process(session.id, asset, events).also { result ->
                if (result.isSuccess) importedSessions += session.id
                else importer.discardImportedCopy(asset.id)
            }
        } catch (error: Exception) {
            importer.discardImportedCopy(asset.id)
            throw error
        }
    }

    override suspend fun share(review: SessionReview, marksOnly: Boolean): ExportState {
        if (!marksOnly) {
            val source = if (review.sessionId in importedSessions) importer else demoSource
            val path = review.recording?.let { source?.localPathOf(it.id) }
            return combinedExporter.share(review, path)
        }
        val export = exports[review.sessionId] ?: exporter.persist(review).getOrElse {
            return ExportState(ExportStatus.ERROR, "Não foi possível preparar as marcas.")
        }.also { exports[review.sessionId] = it }
        return exporter.shareReview(export).fold(
            onSuccess = { ExportState(ExportStatus.SHARED, "Seletor de compartilhamento aberto.") },
            onFailure = { ExportState(ExportStatus.ERROR, "Não foi possível compartilhar as marcas.") },
        )
    }

    override suspend fun discard(review: SessionReview) {
        if (importedSessions.remove(review.sessionId)) review.recording?.let { importer.discardImportedCopy(it.id) }
        exports.remove(review.sessionId)?.let { exporter.discard(it) }
        combinedExporter.discard(review.sessionId)
    }
}
