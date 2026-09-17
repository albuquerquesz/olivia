package com.olivia.companion.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.olivia.companion.R
import com.olivia.core.AudioMode
import kotlin.math.PI
import kotlin.math.sin

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
            .background(MaterialTheme.colorScheme.surfaceVariant)
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
            ?: Color.Transparent,
        animationSpec = OliviaMotion.selection(),
        label = "settings audio mode background",
    )
    val contentColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.surface.takeIf { selected }
            ?: MaterialTheme.colorScheme.onSurfaceVariant,
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

private data class DeviceMetric(
    val icon: ImageVector,
    val value: String,
    val label: String,
)

private val OfflineDeviceMetrics = listOf(
    DeviceMetric(Icons.Outlined.BatteryFull, "78%", "bateria dos óculos"),
    DeviceMetric(Icons.Outlined.Videocam, "3K", "30 fps nativo"),
    DeviceMetric(Icons.Outlined.Storage, "12,4 GB", "livres de 32"),
)

@Composable
internal fun DeviceOverview() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onPrimary) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 260.dp)
                        .padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    DeviceIdentity()
                    DeviceMetrics(OfflineDeviceMetrics)
                }
            }
        }
    }
}

@Composable
private fun DeviceMetrics(metrics: List<DeviceMetric>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        metrics.forEach { metric ->
            DeviceMetricInline(metric, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DeviceMetricInline(metric: DeviceMetric, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "${metric.label}: ${metric.value}"
        },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(metric.icon, contentDescription = null, modifier = Modifier.size(14.dp))
        Text(metric.value, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun DeviceIdentity() {
    DeviceIdentityLayout()
}

@Composable
private fun DeviceIdentityLayout() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DeviceIdentityCopy()
        }
        WayfarerProductImage(Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun DeviceIdentityCopy(modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Text("Ray-Ban Meta", style = MaterialTheme.typography.titleMedium)
        Text(
            "Companion app no celular",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.inverseOnSurface,
        )
    }
}

@Composable
internal fun RecordVideoCta(
    onClick: () -> Unit,
    waveformActive: Boolean = false,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "Oi, Olivia, Gravar",
                    modifier = Modifier.widthIn(max = 220.dp),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Text(
                    "Grave com os óculos.",
                    modifier = Modifier.widthIn(max = 220.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.86f),
                )
            }
            VoiceWaveform(active = waveformActive)
        }
    }
}

@Composable
private fun VoiceWaveform(active: Boolean) {
    val color = MaterialTheme.colorScheme.onPrimary
    val progress = if (active) {
        val transition = rememberInfiniteTransition(label = "voice waveform")
        val animatedProgress by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900),
                repeatMode = RepeatMode.Restart,
            ),
            label = "voice waveform progress",
        )
        animatedProgress
    } else {
        0.5f
    }
    Canvas(modifier = Modifier.size(width = 42.dp, height = 44.dp)) {
        val heights = List(5) { index ->
            val wave = ((sin((progress + index * 0.2f) * 2f * PI) + 1f) / 2f).toFloat()
            0.36f + wave * 0.64f
        }
        val spacing = size.width / heights.size
        heights.forEachIndexed { index, fraction ->
            val x = spacing * index + spacing / 2f
            val halfHeight = size.height * fraction / 2f
            drawLine(
                color = color,
                start = Offset(x, size.height / 2f - halfHeight),
                end = Offset(x, size.height / 2f + halfHeight),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun WayfarerProductImage(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.rayban_meta_wayfarer_gen2),
        contentDescription = "Ray-Ban Meta Wayfarer Gen 2",
        modifier = modifier
            .size(width = 260.dp, height = 120.dp)
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

@Composable
internal fun AudioModeOption(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val selectedBackground = colors.onPrimary.takeIf { selected } ?: colors.primary
    val selectedForeground = colors.primary.takeIf { selected } ?: colors.onPrimary
    val background by animateColorAsState(
        selectedBackground,
        OliviaMotion.selection(),
        label = "audio option background",
    )
    val foreground by animateColorAsState(
        selectedForeground,
        OliviaMotion.selection(),
        label = "audio option foreground",
    )
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        0.97f.takeIf { pressed } ?: 1f,
        OliviaMotion.press(),
        label = "audio option press",
    )
    Surface(
        modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        shape = RoundedCornerShape(16.dp),
        color = background,
        contentColor = foreground,
        border = BorderStroke(1.dp, colors.inverseOnSurface),
    ) {
        Row(
            modifier = Modifier
                .selectable(
                    selected = selected,
                    enabled = enabled,
                    onClick = onClick,
                    role = Role.RadioButton,
                    interactionSource = interactionSource,
                    indication = null,
                )
                .semantics(mergeDescendants = true) {}
                .heightIn(min = 48.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            if (!selected) {
                Spacer(Modifier.size(18.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
