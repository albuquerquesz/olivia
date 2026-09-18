package com.olivia.companion.ui.screens

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.olivia.companion.session.ExportState
import com.olivia.companion.session.ExportStatus
import com.olivia.companion.session.TakeSummary
import com.olivia.companion.session.confirmationLabel
import com.olivia.companion.session.eventTitle
import com.olivia.companion.session.formatOffset
import com.olivia.companion.ui.OliviaPage
import com.olivia.core.CutReason
import com.olivia.core.RecordingAsset
import com.olivia.core.SuggestedCut
import com.olivia.core.TimelineEvent
import com.olivia.core.TimelineEventType
import java.io.File
import kotlin.math.max

private val PreviewShape = RoundedCornerShape(24.dp)

@Composable
fun OliviaTakeScreen(
    take: TakeSummary,
    onNavigateBack: () -> Unit,
    exportState: ExportState = ExportState(),
    onExport: () -> Unit = {},
    recordingPathResolver: suspend (RecordingAsset) -> String? = { null },
) {
    val recording = take.review.recording
    var recordingPath by remember(recording?.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(recording?.id) {
        recordingPath = recording?.let { asset ->
            runCatching { recordingPathResolver(asset) }.getOrNull()
        }
    }

    OliviaPage(onNavigateBack = onNavigateBack) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "Revisão do take",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                reviewSubtitle(take),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        TakePreview(
            title = take.title,
            durationMs = take.durationMs,
            recordingPath = recordingPath,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${take.correctionCount} ${correctionCountLabel(take.correctionCount)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("·", color = MaterialTheme.colorScheme.outline)
            Text(
                "${take.cutCount} ${cutCountLabel(take.cutCount)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (recording != null) {
            OutlinedButton(
                onClick = onExport,
                enabled = exportState.status != ExportStatus.SHARING,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    if (exportState.status == ExportStatus.SHARING) {
                        "Preparando compartilhamento…"
                    } else {
                        "Compartilhar take e marcações"
                    },
                )
            }
        } else {
            Text(
                "O vídeo nativo ainda não está disponível neste ambiente.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        exportState.message?.let { message ->
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SuggestedCutsSection(take)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        TimelineSection(take.events)
    }
}

private fun reviewSubtitle(take: TakeSummary): String = when {
    take.review.recording != null && take.review.transcript.isNotEmpty() ->
        "Transcrição pronta · vídeo nativo registrado"
    take.review.recording != null ->
        "Vídeo nativo registrado · cortes e marcações disponíveis"
    take.review.transcript.isNotEmpty() ->
        "Transcrição pronta · vídeo nativo ainda não disponível"
    else ->
        "Cortes e marcações disponíveis · vídeo nativo ainda não disponível"
}

private fun correctionCountLabel(count: Int): String =
    if (count == 1) "correção" else "correções"

private fun cutCountLabel(count: Int): String =
    if (count == 1) "corte sugerido" else "cortes sugeridos"

@Composable
private fun TakePreview(
    title: String,
    durationMs: Long,
    recordingPath: String?,
) {
    var isPlaying by remember(recordingPath) { mutableStateOf(false) }
    var playbackFailed by remember(recordingPath) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.6f)
            .clip(PreviewShape)
            .background(colors.surfaceVariant),
    ) {
        if (recordingPath != null && !playbackFailed) {
            RecordedVideo(
                path = recordingPath,
                isPlaying = isPlaying,
                onCompleted = { isPlaying = false },
                onError = {
                    isPlaying = false
                    playbackFailed = true
                },
            )
            Surface(
                onClick = { isPlaying = !isPlaying },
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(64.dp),
                shape = CircleShape,
                color = colors.primary,
                contentColor = colors.onPrimary,
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pausar prévia do take" else "Reproduzir prévia do take",
                    modifier = Modifier.padding(18.dp),
                )
            }
        } else {
            PreviewPlaceholder()
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
            color = colors.surface.copy(alpha = 0.94f),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(
                "$title · ${formatOffset(durationMs)}",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecordedVideo(
    path: String,
    isPlaying: Boolean,
    onCompleted: () -> Unit,
    onError: () -> Unit,
) {
    val context = LocalContext.current
    val videoView = remember(path) {
        VideoView(context).apply {
            setVideoURI(Uri.fromFile(File(path)))
            setOnCompletionListener { onCompleted() }
            setOnErrorListener { _, _, _ ->
                onError()
                true
            }
        }
    }

    DisposableEffect(videoView) {
        onDispose { videoView.stopPlayback() }
    }

    AndroidView(
        factory = { videoView },
        modifier = Modifier.fillMaxSize(),
        update = { view ->
            if (isPlaying) {
                view.start()
            } else {
                view.pause()
            }
        },
    )
}

@Composable
private fun PreviewPlaceholder() {
    val colors = MaterialTheme.colorScheme
    val stripeBrush = Brush.linearGradient(
        colors = listOf(colors.surfaceVariant, colors.surface, colors.surfaceVariant),
        start = Offset(0f, 0f),
        end = Offset(48f, 48f),
        tileMode = TileMode.Repeated,
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(stripeBrush),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Prévia do vídeo indisponível",
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun SuggestedCutsSection(take: TakeSummary) {
    val cutEvents = take.events.filter { it.type == TimelineEventType.CUT_SUGGESTED }
    val cuts = take.review.suggestedCuts

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ReviewSectionHeader("Cortes sugeridos")
        ReviewCard {
            when {
                cuts.isNotEmpty() -> {
                    val durationMs = max(
                        take.durationMs,
                        cuts.maxOfOrNull { it.endMs } ?: 0L,
                    )
                    cuts.forEach { cut ->
                        SuggestedCutRow(cut = cut, durationMs = durationMs)
                    }
                }

                cutEvents.isNotEmpty() -> {
                    cutEvents.forEach { event -> CutEventRow(event) }
                }

                else -> {
                    Text(
                        "Nenhum corte sugerido para este take.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "As sugestões aparecem depois do pós-processamento.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestedCutRow(cut: SuggestedCut, durationMs: Long) {
    val total = durationMs.coerceAtLeast(1L)
    val startFraction = (cut.startMs.toFloat() / total).coerceIn(0f, 1f)
    val cutFraction = ((cut.endMs - cut.startMs).toFloat() / total).coerceIn(0.04f, 1f)
    val visibleStart = startFraction.coerceAtMost(0.94f)
    val visibleWidth = cutFraction.coerceAtMost(1f - visibleStart).coerceAtLeast(0.04f)
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 34.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${formatOffset(cut.startMs)}–${formatOffset(cut.endMs)}",
            modifier = Modifier.width(72.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = colors.primary,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(CircleShape)
            .background(colors.surfaceVariant),
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                Spacer(Modifier.weight(visibleStart))
                Box(
                    modifier = Modifier
                        .weight(visibleWidth)
                        .fillMaxHeight()
                        .background(colors.primary, CircleShape),
                )
                val remaining = 1f - visibleStart - visibleWidth
                if (remaining > 0f) {
                    Spacer(Modifier.weight(remaining))
                }
            }
        }
        Text(
            cutReasonLabel(cut.reason),
            modifier = Modifier.widthIn(min = 68.dp, max = 96.dp),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CutEventRow(event: TimelineEvent) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            formatOffset(event.offsetMs),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
        )
        Text(eventTitle(event), style = MaterialTheme.typography.bodyMedium)
    }
}

private fun cutReasonLabel(reason: CutReason): String = when (reason) {
    CutReason.UNCORRECTED_DEVIATION -> "Não confirmada"
    CutReason.DEGRADED_STRETCH -> "Sem frame"
    CutReason.SPEECH_RETAKE -> "Repetição"
    CutReason.SILENCE -> "Silêncio"
}

@Composable
private fun TimelineSection(events: List<TimelineEvent>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ReviewSectionHeader("Timeline", "${events.size} eventos")
        if (events.isEmpty()) {
            Text(
                "Nenhum evento registrado neste take.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column {
                events.forEachIndexed { index, event ->
                    TimelineRow(event = event, isLast = index == events.lastIndex)
                }
            }
        }
    }
}

@Composable
private fun TimelineRow(event: TimelineEvent, isLast: Boolean) {
    val colors = MaterialTheme.colorScheme
    val confirmation = confirmationLabel(event)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val markerX = 6.dp.toPx()
                drawCircle(
                    color = colors.primary,
                    radius = 4.dp.toPx(),
                    center = Offset(markerX, 8.dp.toPx()),
                )
                if (!isLast) {
                    drawLine(
                        color = colors.outlineVariant,
                        start = Offset(markerX, 16.dp.toPx()),
                        end = Offset(markerX, size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
            }
            .padding(start = 24.dp, bottom = 18.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    formatOffset(event.offsetMs),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    timelineEventTag(event),
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = colors.onSurfaceVariant,
                )
            }
            Text(eventTitle(event), style = MaterialTheme.typography.bodyMedium)
            if (confirmation != null) {
                Text(
                    confirmation,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

private fun timelineEventTag(event: TimelineEvent): String = when (event.type) {
    TimelineEventType.SESSION_STARTED,
    TimelineEventType.SESSION_PAUSED,
    TimelineEventType.SESSION_RESUMED,
    TimelineEventType.SESSION_STOPPED -> "SESSÃO"

    TimelineEventType.SESSION_DEGRADED,
    TimelineEventType.SESSION_RECOVERED -> "CONEXÃO"

    TimelineEventType.DIAGNOSIS -> "ANÁLISE"
    TimelineEventType.CORRECTION_ISSUED -> "ORIENTAÇÃO"
    TimelineEventType.CORRECTION_RESOLVED -> "CORREÇÃO RESOLVIDA"
    TimelineEventType.CUT_SUGGESTED -> "CORTE"
    TimelineEventType.ERROR -> "ERRO"
}

@Composable
private fun ReviewSectionHeader(title: String, detail: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (detail != null) {
            Spacer(Modifier.weight(1f))
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReviewCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                PreviewShape,
            ),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = PreviewShape,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}
