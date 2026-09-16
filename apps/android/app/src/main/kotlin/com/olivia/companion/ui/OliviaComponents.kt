package com.olivia.companion.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.olivia.companion.GlassesBackend
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
                .padding(horizontal = 24.dp, vertical = 24.dp),
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
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (degraded) colors.surface else colors.primary,
        contentColor = if (degraded) colors.onSurface else colors.onPrimary,
        shape = RoundedCornerShape(20.dp),
        border = if (degraded) BorderStroke(2.dp, colors.primary) else null,
    ) {
        Column(
            modifier = Modifier.heightIn(min = if (compact) 0.dp else 248.dp).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 24.dp),
            content = content,
        )
    }
}

private data class DeviceMetric(
    val value: String,
    val label: String,
)

private val MockDeviceMetrics = listOf(
    DeviceMetric("78%", "bateria dos óculos"),
    DeviceMetric("3K", "30 fps nativo"),
    DeviceMetric("12,4 GB", "livres de 32"),
)

@Composable
internal fun DeviceOverview(
    backend: GlassesBackend,
    connection: GlassesConnection,
) {
    val simulated = !backend.requiresPhysicalGlasses
    val status = when {
        simulated -> "SIMULADO"
        connection == GlassesConnection.CONNECTED -> "CONECTADO"
        connection == GlassesConnection.DISCONNECTED -> "SEM SINAL"
        else -> "INDISPONÍVEL"
    }
    val connectionDetail = when {
        simulated -> "Bluetooth 5.3 · pareado"
        connection == GlassesConnection.CONNECTED -> "Bluetooth 5.3 · conectado"
        connection == GlassesConnection.DISCONNECTED -> "Bluetooth 5.3 · sem sinal"
        else -> "Bluetooth 5.3 · indisponível"
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                DeviceIdentity(status = status, connectionDetail = connectionDetail)
                if (simulated) {
                    DeviceMetrics(MockDeviceMetrics)
                }
            }
        }
        WakeWordNotice()
    }
}

@Composable
private fun DeviceIdentity(status: String, connectionDetail: String) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val useWideLayout = maxWidth >= 280.dp && fontScale < 1.3f
        if (useWideLayout) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WayfarerSilhouette()
                DeviceIdentityCopy(connectionDetail, Modifier.weight(1f))
                DeviceStatusBadge(status)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WayfarerSilhouette()
                    DeviceIdentityCopy(connectionDetail, Modifier.weight(1f))
                }
                DeviceStatusBadge(status)
            }
        }
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
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val useRow = maxWidth >= 270.dp && fontScale < 1.3f
        if (useRow) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                metrics.forEach { metric ->
                    DeviceMetricCard(metric, Modifier.weight(1f))
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                metrics.forEach { metric ->
                    DeviceMetricCard(metric, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun DeviceMetricCard(metric: DeviceMetric, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.heightIn(min = 76.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f),
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                metric.value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    lineHeight = 24.sp,
                ),
            )
            Text(
                metric.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface,
            )
        }
    }
}

@Composable
private fun WakeWordNotice() {
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
                    "Wake word no celular. O áudio não sai do aparelho.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WayfarerSilhouette() {
    val strokeColor = MaterialTheme.colorScheme.onPrimary
    Canvas(modifier = Modifier.size(width = 80.dp, height = 40.dp)) {
        val strokeWidth = 1.7.dp.toPx()
        val frameStroke = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        val leftLens = Path().apply {
            moveTo(size.width * 0.08f, size.height * 0.26f)
            quadraticBezierTo(
                size.width * 0.11f,
                size.height * 0.13f,
                size.width * 0.20f,
                size.height * 0.12f,
            )
            lineTo(size.width * 0.39f, size.height * 0.12f)
            quadraticBezierTo(
                size.width * 0.46f,
                size.height * 0.15f,
                size.width * 0.45f,
                size.height * 0.34f,
            )
            lineTo(size.width * 0.42f, size.height * 0.70f)
            quadraticBezierTo(
                size.width * 0.40f,
                size.height * 0.86f,
                size.width * 0.31f,
                size.height * 0.87f,
            )
            lineTo(size.width * 0.19f, size.height * 0.87f)
            quadraticBezierTo(
                size.width * 0.09f,
                size.height * 0.84f,
                size.width * 0.08f,
                size.height * 0.67f,
            )
            close()
        }
        val rightLens = Path().apply {
            moveTo(size.width * 0.55f, size.height * 0.34f)
            quadraticBezierTo(
                size.width * 0.54f,
                size.height * 0.15f,
                size.width * 0.61f,
                size.height * 0.12f,
            )
            lineTo(size.width * 0.80f, size.height * 0.12f)
            quadraticBezierTo(
                size.width * 0.89f,
                size.height * 0.13f,
                size.width * 0.92f,
                size.height * 0.26f,
            )
            lineTo(size.width * 0.92f, size.height * 0.67f)
            quadraticBezierTo(
                size.width * 0.91f,
                size.height * 0.84f,
                size.width * 0.81f,
                size.height * 0.87f,
            )
            lineTo(size.width * 0.69f, size.height * 0.87f)
            quadraticBezierTo(
                size.width * 0.60f,
                size.height * 0.86f,
                size.width * 0.58f,
                size.height * 0.70f,
            )
            close()
        }
        drawPath(leftLens, strokeColor, style = frameStroke)
        drawPath(rightLens, strokeColor, style = frameStroke)
        drawLine(
            color = strokeColor,
            start = Offset(size.width * 0.45f, size.height * 0.31f),
            end = Offset(size.width * 0.55f, size.height * 0.31f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = strokeColor,
            start = Offset(size.width * 0.08f, size.height * 0.29f),
            end = Offset(size.width * 0.01f, size.height * 0.19f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = strokeColor,
            start = Offset(size.width * 0.92f, size.height * 0.29f),
            end = Offset(size.width * 0.99f, size.height * 0.19f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = strokeColor,
            radius = 1.8.dp.toPx(),
            center = Offset(size.width * 0.15f, size.height * 0.27f),
        )
        drawCircle(
            color = strokeColor,
            radius = 1.5.dp.toPx(),
            center = Offset(size.width * 0.85f, size.height * 0.27f),
            style = Stroke(width = 1.dp.toPx()),
        )
    }
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
    Surface(
        color = if (inverted) colors.primary else colors.surface,
        contentColor = if (inverted) colors.onPrimary else colors.onSurface,
        shape = CircleShape,
        border = BorderStroke(1.dp, if (inverted) colors.inverseOnSurface else colors.outline),
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
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (onPanel) {
            MaterialTheme.colorScheme.inverseOnSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

@Composable
internal fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
}

@Composable
internal fun AudioModeOption(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(
        if (selected) colors.onPrimary else colors.primary,
        OliviaMotion.selection(),
        label = "audio option background",
    )
    val foreground by animateColorAsState(
        if (selected) colors.primary else colors.onPrimary,
        OliviaMotion.selection(),
        label = "audio option foreground",
    )
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.97f else 1f,
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
            } else {
                Spacer(Modifier.size(18.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
