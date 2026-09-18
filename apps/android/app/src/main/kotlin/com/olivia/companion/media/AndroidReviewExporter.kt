package com.olivia.companion.media

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.olivia.core.ReviewExporter
import com.olivia.core.SessionReview
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LocalReviewExport(
    val reviewJson: File,
    val subtitles: File?,
)

/**
 * Keeps review files in app-private storage until the creator explicitly invokes Android sharing.
 * The original imported video is never altered; the sharesheet grants a one-time read URI only.
 */
class AndroidReviewExporter(context: Context) {
    private val appContext = context.applicationContext
    private val directory = File(appContext.filesDir, "olivia-review-exports")

    init {
        // Review exports are transient handoff artifacts. The UI keeps their ownership only in
        // memory, so anything surviving a process death has no valid owner and is discarded.
        recoverInterruptedExports()
    }

    suspend fun persist(review: SessionReview): Result<LocalReviewExport> = withContext(Dispatchers.IO) {
        runCatching {
            directory.mkdirs()
            val baseName = review.recording?.id ?: review.sessionId.value
            val json = File(directory, "$baseName.review.json")
            json.writeText(ReviewExporter.reviewJson(review), Charsets.UTF_8)
            val subtitles = ReviewExporter.srt(review)?.let { srt ->
                File(directory, "$baseName.srt").also { it.writeText(srt, Charsets.UTF_8) }
            }
            LocalReviewExport(json, subtitles)
        }.mapFailure()
    }

    fun shareVideo(video: File): Result<Unit> = share(
        files = listOf(video),
        mimeType = "video/mp4",
        chooserTitle = "Exportar vídeo para editar",
    )

    fun shareReview(export: LocalReviewExport): Result<Unit> = share(
        files = listOfNotNull(export.reviewJson, export.subtitles),
        mimeType = "application/octet-stream",
        chooserTitle = "Compartilhar marcas e legendas",
    )

    /** Explicitly removes the app-private review artifacts after the creator finishes sharing. */
    suspend fun discard(export: LocalReviewExport) = withContext(Dispatchers.IO) {
        export.reviewJson.delete()
        export.subtitles?.delete()
    }

    private fun recoverInterruptedExports() {
        if (!directory.isDirectory) return
        directory.listFiles()?.forEach { file ->
            if (file.isFile && (file.name.endsWith(".review.json") || file.name.endsWith(".srt"))) {
                file.delete()
            }
        }
    }

    private fun share(files: List<File>, mimeType: String, chooserTitle: String): Result<Unit> = runCatching {
        require(files.isNotEmpty() && files.all(File::isFile)) { "Arquivo local indisponível" }
        val authority = "${appContext.packageName}.fileprovider"
        val uris = files.map { file -> FileProvider.getUriForFile(appContext, authority, file) }
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uris.single())
                clipData = ClipData.newRawUri("olivia-export", uris.single())
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mimeType
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                clipData = ClipData.newRawUri("olivia-export", uris.first())
            }
        }.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(Intent.createChooser(intent, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.mapFailure()

    private fun <T> Result<T>.mapFailure(): Result<T> = fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.failure(IllegalStateException("Exportação local indisponível")) },
    )
}
