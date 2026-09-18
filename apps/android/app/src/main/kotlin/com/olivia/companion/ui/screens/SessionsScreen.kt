package com.olivia.companion.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.olivia.companion.session.TakeSummary
import com.olivia.companion.session.formatOffset
import com.olivia.companion.session.formatRelativeDay
import com.olivia.companion.ui.OliviaPage
import com.olivia.companion.ui.OliviaPanel
import com.olivia.companion.ui.PanelDetail
import com.olivia.core.SessionId

@Composable
fun OliviaSessionsScreen(takes: List<TakeSummary>, onOpenTake: (SessionId) -> Unit) {
    var selectedFilter by remember { mutableStateOf(TakeFilter.ALL) }
    OliviaPage {
        SessionTakeList(
            takes = takes,
            selectedFilter = selectedFilter,
            onFilterSelected = { selectedFilter = it },
            onOpenTake = onOpenTake,
        )
    }
}

@Composable
private fun SessionTakeList(
    takes: List<TakeSummary>,
    selectedFilter: TakeFilter,
    onFilterSelected: (TakeFilter) -> Unit,
    onOpenTake: (SessionId) -> Unit,
) {
    if (takes.isEmpty()) {
        OliviaPanel {
            Text("Seus takes", style = MaterialTheme.typography.bodyLarge)
            Text("Nenhuma sessão ainda", style = MaterialTheme.typography.headlineMedium)
            PanelDetail("Depois da gravação, suas timelines e cortes sugeridos aparecerão aqui.")
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "Takes",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            ),
            modifier = Modifier.semantics { heading() },
        )
        Row(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TakeFilterPill(
                label = "Todos",
                selected = selectedFilter == TakeFilter.ALL,
                onClick = { onFilterSelected(TakeFilter.ALL) },
            )
            TakeFilterPill(
                label = "Com cortes",
                selected = selectedFilter == TakeFilter.WITH_CUTS,
                onClick = { onFilterSelected(TakeFilter.WITH_CUTS) },
            )
            TakeFilterPill(
                label = "Hoje",
                selected = selectedFilter == TakeFilter.TODAY,
                onClick = { onFilterSelected(TakeFilter.TODAY) },
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            takes
                .filter { it.matches(selectedFilter) }
                .chunked(2)
                .forEach { rowTakes ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        rowTakes.forEach { take ->
                            SessionTakeCard(
                                take = take,
                                onClick = { onOpenTake(take.sessionId) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (rowTakes.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
        }
    }
}

private enum class TakeFilter {
    ALL,
    WITH_CUTS,
    TODAY,
}

private fun TakeSummary.matches(filter: TakeFilter): Boolean = when (filter) {
    TakeFilter.ALL -> true
    TakeFilter.WITH_CUTS -> cutCount > 0
    TakeFilter.TODAY -> formatRelativeDay(recordedAtEpochMs) == "Hoje"
}

@Composable
private fun TakeFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
                interactionSource = interactionSource,
                indication = null,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(percent = 50),
            color = colors.onSurface.takeIf { selected } ?: colors.surface,
            contentColor = colors.surface.takeIf { selected } ?: colors.onSurfaceVariant,
            border = BorderStroke(1.dp, colors.outlineVariant).takeIf { !selected },
        ) {
            Text(
                label,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun SessionTakeCard(
    take: TakeSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f),
            ) {
                TakeThumbnail(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp)),
                )
                Text(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp),
                    text = formatOffset(take.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
            val day = formatRelativeDay(take.recordedAtEpochMs)
            val markerCount = take.correctionCount + take.cutCount
            val markerLabel = if (markerCount == 1) "1 marca" else "$markerCount marcas"
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    take.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TakeMetaText(Icons.Outlined.CalendarToday, day)
                    TakeMetaText(Icons.Outlined.Check, markerLabel)
                }
            }
        }
    }
}

@Composable
private fun TakeMetaText(icon: ImageVector, text: String) {
    if (text.isEmpty()) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
