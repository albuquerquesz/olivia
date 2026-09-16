package com.olivia.companion.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.MicNone
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.olivia.companion.R
import com.olivia.companion.session.VoiceCommandState

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
                }
            }
        }
    }
}

@Composable
private fun DeviceIdentity() {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val useWideLayout = maxWidth >= 280.dp && fontScale < 1.3f
        DeviceIdentityLayout(useWideLayout)
    }
}

@Composable
private fun DeviceIdentityLayout(
    useWideLayout: Boolean,
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
            DeviceIdentityCopy(Modifier.weight(1f))
            if (useWideLayout) {
                DeviceLabel()
            }
        }
        if (!useWideLayout) DeviceLabel()
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
private fun DeviceLabel() {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.onPrimary,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        Text(
            "DISPOSITIVO",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
internal fun VoiceAvailabilityNotice(
    voiceState: VoiceCommandState,
    voicePermissionGranted: Boolean,
) {
    val (title, description) = when {
        voiceState == VoiceCommandState.UNAVAILABLE ->
            "Não configurados" to "O modelo local ou o reconhecimento offline ainda não está disponível."
        !voicePermissionGranted ->
            "Permissão necessária" to "Permita o microfone do celular para usar os comandos locais."
        else -> when (voiceState) {
            VoiceCommandState.LISTENING_FOR_WAKE_WORD ->
                "Disponível neste aparelho" to "Diga “Oi, Olivia” e depois gravar, pausar, retomar ou terminar."
            VoiceCommandState.LISTENING_FOR_COMMAND ->
                "Aguardando comando" to "Diga gravar, pausar, retomar ou terminar."
            VoiceCommandState.UNAVAILABLE ->
                "Não configurados" to "O modelo local ou o reconhecimento offline ainda não está disponível."
            VoiceCommandState.ERROR ->
                "Indisponível nesta sessão" to "Não foi possível iniciar o reconhecimento local."
            VoiceCommandState.IDLE ->
                "Disponível neste aparelho" to "Use “Oi, Olivia” e depois diga gravar, pausar, retomar ou terminar."
        }
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.MicNone,
                contentDescription = "Comandos de voz locais",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    "COMANDOS DE VOZ",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Text(
                    description,
                    modifier = Modifier.widthIn(max = 280.dp),
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
