package com.olivia.companion.session

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.olivia.core.SessionReview
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

enum class ExportStatus {
    IDLE,
    SHARING,
    SHARED,
    UNAVAILABLE,
    ERROR,
}

data class ExportState(
    val status: ExportStatus = ExportStatus.IDLE,
    val message: String? = null,
)

interface SessionExportAdapter {
    suspend fun share(review: SessionReview, recordingPath: String?): ExportState
}

/** Compartilha o vídeo nativo e o JSON pelo chooser do Android. */
class AndroidSessionExportAdapter(
    private val context: Context,
) : SessionExportAdapter {
    override suspend fun share(review: SessionReview, recordingPath: String?): ExportState {
        val path = recordingPath?.let(::File)
            ?: return ExportState(
                status = ExportStatus.UNAVAILABLE,
                message = "O vídeo nativo ainda não está disponível neste ambiente.",
            )
        if (!path.isFile) {
            return ExportState(
                status = ExportStatus.UNAVAILABLE,
                message = "O vídeo nativo não está disponível para este take.",
            )
        }

        return try {
            val exportDirectory = File(context.cacheDir, "olivia-exports").apply { mkdirs() }
            val markerFile = File(
                exportDirectory,
                "olivia-markers.json",
            )
            withContext(Dispatchers.IO) {
                markerFile.writeText(OliviaMarkersJson.encode(review), Charsets.UTF_8)
            }
            val videoUri = contentUri(path)
            val markerUri = contentUri(markerFile)
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(
                    Intent.EXTRA_STREAM,
                    arrayListOf<Uri>(videoUri, markerUri),
                )
                clipData = ClipData.newUri(context.contentResolver, "Olivia", videoUri).apply {
                    addItem(ClipData.Item(markerUri))
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Enviar take e marcações"))
            ExportState(
                status = ExportStatus.SHARED,
                message = "Seletor de compartilhamento aberto. A importação no CapCut depende do aparelho.",
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            ExportState(
                status = ExportStatus.ERROR,
                message = "Não foi possível preparar o compartilhamento deste take.",
            )
        }
    }

    private fun contentUri(file: File): Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )

}
