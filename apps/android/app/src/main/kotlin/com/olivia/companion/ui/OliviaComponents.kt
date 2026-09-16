package com.olivia.companion.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.olivia.companion.GlassesBackend
import com.olivia.companion.session.VoiceCommandState
import com.olivia.companion.R
import com.olivia.core.GlassesConnection

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
                IconButton(onClick = onNavigateBack) {
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
internal fun DeviceOverview(
    backend: GlassesBackend,
    connection: GlassesConnection,
    voiceState: VoiceCommandState,
    voicePermissionGranted: Boolean,
) {
    val simulated = !backend.requiresPhysicalGlasses
    val status = deviceStatus(simulated, connection)
    val connectionDetail = deviceConnectionDetail(simulated, connection)

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
                        .heightIn(min = 220.dp)
                        .padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    DeviceIdentity(status = status, connectionDetail = connectionDetail)
                    if (simulated) {
                        DeviceMetrics(OfflineDeviceMetrics)
                    }
                }
            }
        }
        WakeWordNotice(voiceState, voicePermissionGranted)
    }
}

private fun deviceStatus(simulated: Boolean, connection: GlassesConnection): String {
    if (simulated) return "SIMULADO"
    return when (connection) {
        GlassesConnection.CONNECTED -> "CONECTADO"
        GlassesConnection.DISCONNECTED -> "SEM SINAL"
        GlassesConnection.UNAVAILABLE -> "INDISPONÍVEL"
    }
}

private fun deviceConnectionDetail(simulated: Boolean, connection: GlassesConnection): String {
    if (simulated) return "Bluetooth 5.3 · pareado"
    return when (connection) {
        GlassesConnection.CONNECTED -> "Bluetooth 5.3 · conectado"
        GlassesConnection.DISCONNECTED -> "Bluetooth 5.3 · sem sinal"
        GlassesConnection.UNAVAILABLE -> "Bluetooth 5.3 · indisponível"
    }
}

@Composable
private fun DeviceIdentity(status: String, connectionDetail: String) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val useWideLayout = maxWidth >= 280.dp && fontScale < 1.3f
        DeviceIdentityLayout(useWideLayout, status, connectionDetail)
    }
}

@Composable
private fun DeviceIdentityLayout(
    useWideLayout: Boolean,
    status: String,
    connectionDetail: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DeviceIdentityCopy(connectionDetail, Modifier.weight(1f))
            if (useWideLayout) {
                DeviceStatusBadge(status)
            }
        }
        if (!useWideLayout) {
            DeviceStatusBadge(status)
        }
        WayfarerProductImage(Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun DeviceIdentityCopy(connectionDetail: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Text("Wayfarer Gen 2", style = MaterialTheme.typography.titleMedium)
        Text(
            connectionDetail,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.inverseOnSurface,
        )
    }
}

@Composable
private fun DeviceStatusBadge(status: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.onPrimary,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        Text(
            status,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
        )
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
        Icon(metric.icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Text(metric.value, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun WakeWordNotice(
    voiceState: VoiceCommandState,
    voicePermissionGranted: Boolean,
) {
    val (title, description) = when {
        voiceState == VoiceCommandState.UNAVAILABLE ->
            "Comandos de voz indisponíveis" to "Porcupine ou o reconhecimento offline ainda não foi configurado."
        !voicePermissionGranted ->
            "Microfone necessário" to "Permita o microfone do celular para usar os comandos locais."
        else -> when (voiceState) {
            VoiceCommandState.LISTENING_FOR_WAKE_WORD ->
                "Comandos de voz locais" to "Diga “Oi, Olivia” e depois gravar, pausar, retomar ou terminar."
            VoiceCommandState.LISTENING_FOR_COMMAND ->
                "Comando de voz" to "Diga gravar, pausar, retomar ou terminar."
            VoiceCommandState.UNAVAILABLE ->
                "Comandos de voz indisponíveis" to "Porcupine ou o reconhecimento offline ainda não foi configurado."
            VoiceCommandState.ERROR ->
                "Comandos de voz indisponíveis" to "Não foi possível iniciar o reconhecimento local nesta sessão."
            VoiceCommandState.IDLE ->
                "Comandos de voz locais" to "Use “Oi, Olivia” e depois diga gravar, pausar, retomar ou terminar."
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VoiceWaveform()
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("“Oi, Olivia, gravar”", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Wake word local planejado. Ainda não está ativo nesta versão.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
                translationY = -16.dp.toPx()
                clip = false
            },
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun VoiceWaveform() {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.size(width = 30.dp, height = 32.dp)) {
        val heights = listOf(0.72f, 1f, 0.52f, 0.76f, 0.36f)
        val spacing = size.width / heights.size
        heights.forEachIndexed { index, fraction ->
            val x = spacing * index + spacing / 2f
            val halfHeight = size.height * fraction / 2f
            drawLine(
                color = color,
                start = Offset(x, size.height / 2f - halfHeight),
                end = Offset(x, size.height / 2f + halfHeight),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
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
