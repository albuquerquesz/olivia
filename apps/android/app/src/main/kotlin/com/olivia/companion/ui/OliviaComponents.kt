package com.olivia.companion.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.olivia.companion.R
import com.olivia.core.AudioMode
import com.olivia.core.GlassesConnection

internal enum class OliviaButtonVariant {
    Primary,
    PrimaryGradient,
}

@Composable
internal fun OliviaButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: OliviaButtonVariant = OliviaButtonVariant.Primary,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp.takeIf { variant == OliviaButtonVariant.PrimaryGradient } ?: 20.dp)
    val isGradient = variant == OliviaButtonVariant.PrimaryGradient
    val contentColor = when {
        !enabled -> colors.onSurfaceVariant
        isGradient -> OliviaBlueActionOn
        else -> colors.onPrimary
    }
    val background = when {
        !enabled -> Brush.verticalGradient(listOf(colors.surfaceVariant, colors.surfaceVariant))
        isGradient -> Brush.verticalGradient(listOf(OliviaBlueActionStart, OliviaBlueActionEnd))
        else -> Brush.verticalGradient(listOf(colors.primary, colors.primary))
    }
    val shadowModifier = if (isGradient && enabled) {
        Modifier.shadow(
            elevation = 4.dp,
            shape = shape,
            ambientColor = OliviaBlueActionShadow,
            spotColor = OliviaBlueActionShadow,
        )
    } else {
        Modifier
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = 48.dp)
            .then(shadowModifier),
        shape = shape,
        color = Color.Transparent,
        contentColor = contentColor,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(background, shape)
                .padding(horizontal = 24.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

@Composable
internal fun OliviaPage(
    onNavigateBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            if (onNavigateBack != null) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = CircleShape,
                    ),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Voltar")
                }
            }
            content()
        }
    }
}

@Composable
internal fun OliviaPanel(
    degraded: Boolean = false,
    compact: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val panelColor = colors.surface.takeIf { degraded } ?: colors.primary
    val panelContentColor = colors.onSurface.takeIf { degraded } ?: colors.onPrimary
    val panelBorder = degraded.takeIf { it }?.let { BorderStroke(2.dp, colors.primary) }
    val panelMinHeight = 0.dp.takeIf { compact } ?: 248.dp
    val panelSpacing = 16.dp.takeIf { compact } ?: 24.dp
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = panelColor,
        contentColor = panelContentColor,
        shape = RoundedCornerShape(20.dp),
        border = panelBorder,
    ) {
        Column(
            modifier = Modifier
                .heightIn(min = panelMinHeight)
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(panelSpacing),
            content = content,
        )
    }
}

@Composable
internal fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
internal fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.settingsCardEdgeToEdge(),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

private fun Modifier.settingsCardEdgeToEdge(): Modifier = layout { measurable, constraints ->
    val inset = 20.dp.roundToPx()
    val expandedMaxWidth = if (constraints.maxWidth == Constraints.Infinity) {
        Constraints.Infinity
    } else {
        constraints.maxWidth + inset * 2
    }
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.minWidth + inset * 2,
            maxWidth = expandedMaxWidth,
        ),
    )
    layout(
        width = (placeable.width - inset * 2).coerceAtLeast(0),
        height = placeable.height,
    ) {
        placeable.placeRelative(-inset, 0)
    }
}

@Composable
internal fun SettingsOverline(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall.copy(
            fontSize = 18.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.sp,
            fontWeight = FontWeight.Bold,
        ),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.95f),
    )
}

@Composable
internal fun SettingsAudioModeSelector(
    audioMode: AudioMode,
    enabled: Boolean,
    onAudioMode: (AudioMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsAudioModeOption(
            label = "Modo voz",
            selected = audioMode == AudioMode.VOICE,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            onClick = { onAudioMode(AudioMode.VOICE) },
        )
        SettingsAudioModeOption(
            label = "Modo sinal",
            selected = audioMode == AudioMode.SIGNAL,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            onClick = { onAudioMode(AudioMode.SIGNAL) },
        )
    }
}

@Composable
private fun SettingsAudioModeOption(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val background by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.onSurface.takeIf { selected }
            ?: MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = OliviaMotion.selection(),
        label = "settings audio mode background",
    )
    val contentColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.surface.takeIf { selected }
            ?: MaterialTheme.colorScheme.onSurface,
        animationSpec = OliviaMotion.selection(),
        label = "settings audio mode content",
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background, RoundedCornerShape(16.dp))
            .selectable(
                selected = selected,
                enabled = enabled,
                onClick = onClick,
                role = Role.RadioButton,
            )
            .semantics(mergeDescendants = true) {},
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = contentColor)
    }
}

@Composable
internal fun SettingsMetricRow(
    title: String,
    description: String,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                ),
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Normal,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
            )
        }
        trailing()
    }
}

@Composable
internal fun DeviceOverview(connection: GlassesConnection) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 148.dp)
                .padding(start = 20.dp, top = 16.dp, end = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "ORIENTAÇÃO POR ÁUDIO",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Ray-Ban Meta",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DeviceMetaBadge(Icons.Outlined.BatteryStd, "--%")
                    DeviceMetaBadge(Icons.Outlined.Videocam, "1920 × 1080")
                }
                ConnectionStatusBadge(
                    connection = connection,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Box(
                modifier = Modifier
                    .size(width = 164.dp, height = 116.dp),
                contentAlignment = Alignment.Center,
            ) {
                WayfarerProductImage(
                    width = 164.dp,
                    height = 84.dp,
                )
            }
        }
    }
}

private data class ConnectionStatusStyle(
    val label: String,
    val containerColor: Color,
    val contentColor: Color,
)

@Composable
private fun ConnectionStatusBadge(
    connection: GlassesConnection,
    modifier: Modifier = Modifier,
) {
    val style = when (connection) {
        GlassesConnection.CONNECTED -> ConnectionStatusStyle(
            label = "Conectado",
            containerColor = OliviaStatusConnected,
            contentColor = OliviaStatusConnectedOn,
        )
        GlassesConnection.DISCONNECTED -> ConnectionStatusStyle(
            label = "Sem frame",
            containerColor = OliviaStatusWarning,
            contentColor = OliviaStatusWarningOn,
        )
        GlassesConnection.UNAVAILABLE -> ConnectionStatusStyle(
            label = "Indisponível",
            containerColor = OliviaStatusUnavailable,
            contentColor = OliviaStatusUnavailableOn,
        )
    }
    Surface(
        modifier = modifier.semantics {
            contentDescription = "Status da conexão dos óculos: ${style.label}"
        },
        shape = RoundedCornerShape(percent = 50),
        color = style.containerColor,
        contentColor = style.contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(style.contentColor, CircleShape),
            )
            Text(
                style.label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DeviceMetaBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
) {
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(12.dp))
            Text(
                text,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun WayfarerProductImage(
    modifier: Modifier = Modifier,
    width: Dp = 260.dp,
    height: Dp = 120.dp,
) {
    Image(
        painter = painterResource(R.drawable.rayban_meta_wayfarer_gen2),
        contentDescription = "Ray-Ban Meta Wayfarer Gen 2",
        modifier = modifier
            .size(width = width, height = height)
            .graphicsLayer {
                translationY = 8.dp.toPx()
                clip = false
            },
        contentScale = ContentScale.Fit,
    )
}

@Composable
internal fun StatusChip(text: String, inverted: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    val containerColor = colors.primary.takeIf { inverted } ?: colors.surface
    val contentColor = colors.onPrimary.takeIf { inverted } ?: colors.onSurface
    val borderColor = colors.inverseOnSurface.takeIf { inverted } ?: colors.outline
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = CircleShape,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
internal fun PanelDetail(text: String, onPanel: Boolean = true) {
    val textColor = MaterialTheme.colorScheme.inverseOnSurface.takeIf { onPanel }
        ?: MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = textColor,
    )
}

@Composable
internal fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
}
