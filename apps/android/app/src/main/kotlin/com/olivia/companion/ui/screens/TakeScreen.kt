package com.olivia.companion.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.olivia.companion.session.ExportState
import com.olivia.companion.session.ExportStatus
import com.olivia.companion.session.TakeSummary
import com.olivia.companion.session.confirmationLabel
import com.olivia.companion.session.eventTitle
import com.olivia.companion.session.formatOffset
import com.olivia.companion.session.isConfirmed
import com.olivia.companion.ui.OliviaPage
import com.olivia.companion.ui.OliviaPanel
import com.olivia.companion.ui.PanelDetail
import com.olivia.companion.ui.SectionTitle
import com.olivia.companion.ui.StatusChip
import com.olivia.core.CutReason
import com.olivia.core.SuggestedCut
import com.olivia.core.TimelineEvent
import com.olivia.core.TimelineEventType

@Composable
fun OliviaTakeScreen(
    take: TakeSummary,
    onNavigateBack: () -> Unit,
    exportState: ExportState = ExportState(),
    onExport: () -> Unit = {},
) {
    OliviaPage(onNavigateBack = onNavigateBack) {
        OliviaPanel {
            Text("Revisão", style = MaterialTheme.typography.bodyLarge)
            Text(formatOffset(take.durationMs), style = MaterialTheme.typography.headlineMedium)
            PanelDetail("${take.correctionCount} correções · ${take.cutCount} cortes sugeridos")
            if (take.review.recording != null) {
                OutlinedButton(
                    onClick = onExport,
                    enabled = exportState.status != ExportStatus.SHARING,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = RoundedCornerShape(20.dp),
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
                    style = MaterialTheme.typography.bodyMedium,
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
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("Cortes sugeridos")
            SuggestedCuts(take)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("Timeline")
            take.events.forEach { event -> TimelineRow(event) }
        }
    }
}

@Composable
private fun SuggestedCuts(take: TakeSummary) {
    val cutEvents = take.events.filter { it.type == TimelineEventType.CUT_SUGGESTED }
    if (cutEvents.isEmpty() && take.review.suggestedCuts.isEmpty()) {
        Text(
            "Nenhum corte neste take. Os cortes aparecem quando o pós-processamento terminar.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    if (cutEvents.isNotEmpty()) {
        cutEvents.forEach { event -> TimelineRow(event) }
        return
    }
    take.review.suggestedCuts.forEach { cut ->
        SuggestedCutRow(cut)
    }
}

@Composable
private fun SuggestedCutRow(cut: SuggestedCut) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "${formatOffset(cut.startMs)} a ${formatOffset(cut.endMs)}",
            style = MaterialTheme.typography.labelLarge,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(cutReasonLabel(cut.reason), style = MaterialTheme.typography.bodyLarge)
            StatusChip("Sugestão", inverted = false)
        }
    }
}

private fun cutReasonLabel(reason: CutReason): String = when (reason) {
    CutReason.UNCORRECTED_DEVIATION -> "Correção não confirmada"
    CutReason.DEGRADED_STRETCH -> "Trecho sem frame"
    CutReason.SPEECH_RETAKE -> "Repetição na fala"
    CutReason.SILENCE -> "Silêncio"
}

@Composable
private fun TimelineRow(event: TimelineEvent) {
    val confirmation = confirmationLabel(event)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(formatOffset(event.offsetMs), style = MaterialTheme.typography.labelLarge)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(eventTitle(event), style = MaterialTheme.typography.bodyLarge)
            if (confirmation != null) {
                StatusChip(
                    confirmation,
                    inverted = isConfirmed(event),
                )
            }
        }
    }
}
