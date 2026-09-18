package com.olivia.companion.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.olivia.companion.session.OliviaUiState
import com.olivia.companion.session.TakeSummary
import com.olivia.companion.session.formatOffset
import com.olivia.companion.session.formatRelativeDay
import com.olivia.companion.R
import com.olivia.companion.ui.DeviceOverview
import com.olivia.companion.ui.OliviaPage
import com.olivia.core.SessionId

@Composable
fun OliviaHomeScreen(
    state: OliviaUiState,
    onSeeAllTakes: () -> Unit,
    onOpenRecord: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTake: (SessionId) -> Unit,
) {
    val recent = state.takes.take(3)
    OliviaPage {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HomeHeader()
            DeviceOverview()
            VoiceCommandBar(onOpenRecord = onOpenRecord)
            HomeQuickActions(
                onOpenRecord = onOpenRecord,
                onSeeAllTakes = onSeeAllTakes,
                onOpenSettings = onOpenSettings,
            )
            if (recent.isEmpty()) {
                EmptyTakesCard(onSeeAllTakes = onSeeAllTakes)
            } else {
                RecentTakesCard(recent, onSeeAllTakes, onOpenTake)
            }
        }
    }
}

@Composable
private fun HomeHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.olivia_logo),
            contentDescription = "Logo da Olivia",
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp)),
        )
        Text(
            "Olivia",
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 20.sp,
                lineHeight = 24.sp,
            ),
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun VoiceCommandBar(onOpenRecord: () -> Unit) {
    Surface(
        onClick = onOpenRecord,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Outlined.Mic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Controle por voz",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Oi, Olivia, gravar",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = "Abrir gravação",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HomeQuickActions(
    onOpenRecord: () -> Unit,
    onSeeAllTakes: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HomeQuickAction(
            label = "Gravar",
            icon = Icons.Outlined.Mic,
            highlighted = true,
            onClick = onOpenRecord,
        )
        HomeQuickAction(
            label = "Takes",
            icon = Icons.Outlined.History,
            onClick = onSeeAllTakes,
        )
        HomeQuickAction(
            label = "Ajustes",
            icon = Icons.Outlined.Settings,
            onClick = onOpenSettings,
        )
    }
}

@Composable
private fun RowScope.HomeQuickAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp),
            shape = RoundedCornerShape(18.dp),
            color = if (highlighted) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surface,
            contentColor = if (highlighted) Color.White
            else MaterialTheme.colorScheme.onSurface,
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = label, modifier = Modifier.size(28.dp))
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun RecentTakes(
    takes: List<TakeSummary>,
    onOpenTake: (SessionId) -> Unit,
) {
    takes.forEachIndexed { index, take ->
        TakePreviewCard(
            take = take,
            featured = index == 0,
            onClick = { onOpenTake(take.sessionId) },
        )
    }
}

@Composable
private fun RecentTakesHeader(onSeeAllTakes: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Últimos takes",
            modifier = Modifier.align(Alignment.CenterVertically),
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
        )
        TextButton(
            onClick = onSeeAllTakes,
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .heightIn(min = 48.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "Ver mais",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Normal),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                )
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun RecentTakesCard(
    takes: List<TakeSummary>,
    onSeeAllTakes: () -> Unit,
    onOpenTake: (SessionId) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RecentTakesHeader(onSeeAllTakes)
        RecentTakes(takes, onOpenTake)
    }
}

@Composable
private fun EmptyTakesCard(
    onSeeAllTakes: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 248.dp)
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RecentTakesHeader(onSeeAllTakes)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                Icon(
                    Icons.Outlined.Videocam,
                    contentDescription = "Câmera de gravação",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                )
                Text(
                    "Seu próximo take aparece aqui",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "Grave um take para ver a timeline e os cortes sugeridos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun TakePreviewCard(
    take: TakeSummary,
    featured: Boolean,
    onClick: () -> Unit,
) {
    val cardColor = if (featured) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val cardBorder = if (featured) {
        null
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (featured) 132.dp else 88.dp),
        shape = RoundedCornerShape(20.dp),
        color = cardColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = cardBorder,
    ) {
        Row(
            modifier = Modifier.padding(if (featured) 16.dp else 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TakeThumbnail(
                modifier = Modifier
                    .size(if (featured) 88.dp else 64.dp)
                    .clip(RoundedCornerShape(if (featured) 18.dp else 14.dp)),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(if (featured) 8.dp else 6.dp),
            ) {
                if (featured) {
                    Text(
                        "Último take",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    take.title,
                    style = if (featured) {
                        MaterialTheme.typography.titleLarge
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val recorded = formatRelativeDay(take.recordedAtEpochMs)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TakeMetaBadge(Icons.Outlined.Schedule, formatOffset(take.durationMs))
                    TakeMetaBadge(Icons.Outlined.CalendarToday, recorded)
                }
                if (featured) {
                    Text(
                        "${take.correctionCount} correções · ${take.cutCount} cortes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TakeMetaBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    if (text.isEmpty()) return
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
