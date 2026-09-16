package com.olivia.companion.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.olivia.companion.R
import com.olivia.companion.session.OliviaUiState
import com.olivia.companion.session.TakeSummary
import com.olivia.companion.session.confirmationLabel
import com.olivia.companion.session.eventTitle
import com.olivia.companion.session.formatOffset
import com.olivia.companion.session.formatRelativeDay
import com.olivia.companion.session.isConfirmed
import com.olivia.companion.session.sessionStateLabel
import com.olivia.core.AudioMode
import com.olivia.core.CutReason
import com.olivia.core.SessionId
import com.olivia.core.SessionState
import com.olivia.core.SuggestedCut
import com.olivia.core.TimelineEvent
import com.olivia.core.TimelineEventType

private val PrivacyCopy =
    "Frames de análise são efêmeros. O áudio do celular não alimenta IA durante o take."

@Composable
fun OliviaWelcomeScreen(onEnter: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .widthIn(max = 600.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.welcome_title),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.welcome_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Button(
                onClick = onEnter,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(stringResource(R.string.welcome_enter), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun OliviaHomeScreen(
    state: OliviaUiState,
    voicePermissionGranted: Boolean,
    onSeeAllTakes: () -> Unit,
    onOpenTake: (SessionId) -> Unit,
) {
    val recent = state.takes.take(3)
    OliviaPage {
        DeviceOverview(
            backend = state.backend,
            connection = state.connection,
            voiceState = state.voiceState,
            voicePermissionGranted = voicePermissionGranted,
        )
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Últimos takes".uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "ver todos",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable(onClick = onSeeAllTakes)
                        .semantics { role = Role.Button },
                )
            }
            RecentTakes(recent, onOpenTake)
        }
    }
}

@Composable
private fun RecentTakes(takes: List<TakeSummary>, onOpenTake: (SessionId) -> Unit) {
    if (takes.isEmpty()) {
        Text(
            "Depois da gravação, as timelines aparecem aqui.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    takes.forEach { take ->
        TakePreviewCard(take = take, onClick = { onOpenTake(take.sessionId) })
    }
}

@Composable
private fun TakePreviewCard(take: TakeSummary, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TakeThumbnail()
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    take.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val recorded = formatRelativeDay(take.recordedAtEpochMs)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TakeMetaBadge(Icons.Outlined.Schedule, formatOffset(take.durationMs))
                    TakeMetaBadge(Icons.Outlined.CalendarToday, recorded)
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
private fun TakeMetaBadge(icon: ImageVector, text: String) {
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

@Composable
private fun TakeThumbnail() {
    val stripe = MaterialTheme.colorScheme.outlineVariant
    val ground = MaterialTheme.colorScheme.surfaceVariant
    Canvas(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(16.dp)),
    ) {
        drawRect(ground)
        val step = 8.dp.toPx()
        var x = -size.height
        while (x < size.width + size.height) {
            drawLine(
                color = stripe,
                start = Offset(x, size.height),
                end = Offset(x + size.height, 0f),
                strokeWidth = 4.dp.toPx(),
            )
            x += step
        }
    }
}

@Composable
fun OliviaRecordingScreen(
    state: OliviaUiState,
    onOpenSettings: () -> Unit,
    onAudioMode: (AudioMode) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDegrade: () -> Unit,
    onRecover: () -> Unit,
    onStop: () -> Unit,
) {
    val session = state.session
    val sessionState = session?.state ?: SessionState.IDLE
    val degraded = sessionState == SessionState.DEGRADED
    val live = state.isLive
    OliviaPage {
        OliviaPanel(
            degraded = degraded,
            compact = true,
            contentPadding = PaddingValues(0.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 400.dp, max = 480.dp),
            ) {
                GlassesCameraPreview(
                    elapsedMs = state.elapsedMs,
                    degraded = degraded,
                    simulated = !state.backend.requiresPhysicalGlasses,
                    modifier = Modifier.fillMaxSize(),
                )
                SessionStateBadge(
                    state = sessionState,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(20.dp),
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(20.dp),
                ) {
                    Text(formatOffset(state.elapsedMs), style = MaterialTheme.typography.headlineSmall)
                }
                if (degraded || state.backend.requiresPhysicalGlasses) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(20.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.76f),
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text = recordingPanelMessage(
                                degraded = degraded,
                                requiresPhysicalGlasses = state.backend.requiresPhysicalGlasses,
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
        if (!state.backend.requiresPhysicalGlasses) {
            DebugActions(
                sessionState = sessionState,
                onStart = onStart,
                onPause = onPause,
                onResume = onResume,
                onDegrade = onDegrade,
                onRecover = onRecover,
                onStop = onStop,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("Modo de feedback")
            FlowRow(
                modifier = Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AudioModeOption("Voz", state.audioMode == AudioMode.VOICE, enabled = !live) {
                    onAudioMode(AudioMode.VOICE)
                }
                AudioModeOption("Sinal", state.audioMode == AudioMode.SIGNAL, enabled = !live) {
                    onAudioMode(AudioMode.SIGNAL)
                }
            }
        }
        OutlinedButton(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            shape = RoundedCornerShape(20.dp),
        ) {
            Text("Configurar áudio e dispositivo", modifier = Modifier.weight(1f))
            Icon(
                Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                modifier = Modifier.padding(start = 8.dp).size(20.dp),
            )
        }
    }
}

@Composable
private fun SessionStateBadge(state: SessionState, modifier: Modifier = Modifier) {
    val degraded = state == SessionState.DEGRADED
    val containerColor = MaterialTheme.colorScheme.onPrimary.takeIf { !degraded }
        ?: MaterialTheme.colorScheme.surface
    val contentColor = MaterialTheme.colorScheme.primary.takeIf { !degraded }
        ?: MaterialTheme.colorScheme.onSurface
    Surface(
        modifier = modifier,
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            sessionStateLabel(state),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun GlassesCameraPreview(
    elapsedMs: Long,
    degraded: Boolean,
    simulated: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val description = cameraPreviewDescription(simulated)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .semantics { contentDescription = description },
    ) {
        if (simulated) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val horizonY = size.height * 0.58f
                val hasFramingIssue = elapsedMs in 8_000L..20_000L
                val hasShadowIssue = elapsedMs in 28_000L..36_000L
                val hasHorizonIssue = elapsedMs in 38_000L..48_000L
                val subjectCenter = subjectCenter(size.width, hasFramingIssue)
                val skinColor = Color(0xFFD0A58C)
                val tableColor = Color(0xFF63717A)

                drawRect(Color(0xFF8B9892))
                drawRect(
                    color = tableColor,
                    topLeft = Offset(0f, horizonY),
                    size = Size(size.width, size.height - horizonY),
                )
                drawLine(
                    color = colors.onPrimary.copy(alpha = 0.55f),
                    start = Offset(0f, horizonY),
                    end = Offset(
                        size.width,
                        horizonY + horizonOffset(hasHorizonIssue, 24.dp.toPx()),
                    ),
                    strokeWidth = 1.5.dp.toPx(),
                )
                drawRoundRect(
                    color = skinColor,
                    topLeft = Offset(subjectCenter - size.width * 0.18f, size.height * 0.24f),
                    size = Size(size.width * 0.36f, size.height * 0.76f),
                    cornerRadius = CornerRadius(32.dp.toPx(), 32.dp.toPx()),
                )
                drawCircle(
                    color = Color(0xFF5E433A),
                    radius = 9.dp.toPx(),
                    center = Offset(subjectCenter + size.width * 0.02f, size.height * 0.57f),
                )
                drawLine(
                    color = Color(0xFF3E3432),
                    start = Offset(subjectCenter + size.width * 0.08f, size.height * 0.54f),
                    end = Offset(subjectCenter + size.width * 0.19f, size.height * 0.45f),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                if (hasShadowIssue) {
                    drawRect(
                        color = colors.onSurface.copy(alpha = 0.24f),
                        topLeft = Offset(subjectCenter - size.width * 0.18f, size.height * 0.24f),
                        size = Size(size.width * 0.36f, size.height * 0.34f),
                    )
                }

                val guideColor = colors.onPrimary.copy(alpha = 0.85f)
                val guideLength = 18.dp.toPx()
                val guideInset = 14.dp.toPx()
                drawLine(guideColor, Offset(guideInset, guideInset), Offset(guideInset + guideLength, guideInset), 2.dp.toPx())
                drawLine(guideColor, Offset(guideInset, guideInset), Offset(guideInset, guideInset + guideLength), 2.dp.toPx())
                drawLine(guideColor, Offset(size.width - guideInset, guideInset), Offset(size.width - guideInset - guideLength, guideInset), 2.dp.toPx())
                drawLine(guideColor, Offset(size.width - guideInset, guideInset), Offset(size.width - guideInset, guideInset + guideLength), 2.dp.toPx())
                drawLine(guideColor, Offset(guideInset, size.height - guideInset), Offset(guideInset + guideLength, size.height - guideInset), 2.dp.toPx())
                drawLine(guideColor, Offset(guideInset, size.height - guideInset), Offset(guideInset, size.height - guideInset - guideLength), 2.dp.toPx())
                drawLine(guideColor, Offset(size.width - guideInset, size.height - guideInset), Offset(size.width - guideInset - guideLength, size.height - guideInset), 2.dp.toPx())
                drawLine(guideColor, Offset(size.width - guideInset, size.height - guideInset), Offset(size.width - guideInset, size.height - guideInset - guideLength), 2.dp.toPx())
            }
        }
        if (!simulated) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = colors.surfaceVariant,
                contentColor = colors.onSurfaceVariant,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("Aguardando câmera dos óculos", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (degraded) {
            Surface(
                modifier = Modifier.align(Alignment.Center),
                color = colors.onSurface.copy(alpha = 0.76f),
                contentColor = colors.onPrimary,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    "Sem frame",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

private fun recordingPanelMessage(
    degraded: Boolean,
    requiresPhysicalGlasses: Boolean,
): String {
    if (degraded) {
        return "A Olivia parou de ver a câmera. As marcas ficam; a gravação nativa continua."
    }
    if (requiresPhysicalGlasses) return "É necessário conectar os óculos para gravar."
    return "Prévia do fluxo, com óculos simulados."
}

private fun cameraPreviewDescription(simulated: Boolean): String {
    if (simulated) return "Câmera simulada dos óculos, formato vertical 720 por 1280"
    return "Prévia da câmera dos óculos indisponível"
}

private fun subjectCenter(width: Float, hasFramingIssue: Boolean): Float {
    if (hasFramingIssue) return width * 0.76f
    return width * 0.5f
}

private fun horizonOffset(hasHorizonIssue: Boolean, offset: Float): Float {
    if (hasHorizonIssue) return offset
    return 0f
}

@Composable
fun OliviaSessionsScreen(takes: List<TakeSummary>, onOpenTake: (SessionId) -> Unit) {
    OliviaPage {
        SessionTakeList(takes, onOpenTake)
    }
}

@Composable
private fun SessionTakeList(takes: List<TakeSummary>, onOpenTake: (SessionId) -> Unit) {
    if (takes.isEmpty()) {
        OliviaPanel {
            Text("Seus takes", style = MaterialTheme.typography.bodyLarge)
            Text("Nenhuma sessão ainda", style = MaterialTheme.typography.headlineMedium)
            PanelDetail("Depois da gravação, suas timelines e cortes sugeridos aparecerão aqui.")
        }
        return
    }
    SectionTitle("Seus takes")
    takes.forEach { take ->
        OutlinedButton(
            onClick = { onOpenTake(take.sessionId) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(take.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${formatOffset(take.durationMs)} · ${take.correctionCount} correções · ${take.cutCount} cortes",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
        }
    }
}

@Composable
fun OliviaTakeScreen(take: TakeSummary, onNavigateBack: () -> Unit) {
    OliviaPage(onNavigateBack = onNavigateBack) {
        OliviaPanel {
            Text("Revisão", style = MaterialTheme.typography.bodyLarge)
            Text(formatOffset(take.durationMs), style = MaterialTheme.typography.headlineMedium)
            PanelDetail("${take.correctionCount} correções · ${take.cutCount} cortes sugeridos")
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
fun OliviaSettingsScreen(
    audioMode: AudioMode,
    live: Boolean,
    onAudioMode: (AudioMode) -> Unit,
) {
    OliviaPage {
        OliviaPanel {
            SectionTitle("Modo de feedback")
            FlowRow(
                modifier = Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AudioModeOption("Voz", audioMode == AudioMode.VOICE, enabled = !live) {
                    onAudioMode(AudioMode.VOICE)
                }
                AudioModeOption("Sinal", audioMode == AudioMode.SIGNAL, enabled = !live) {
                    onAudioMode(AudioMode.SIGNAL)
                }
            }
            PanelDetail(
                audioModeDescription(audioMode),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("Meu perfil")
            Text("Mariana · Tatuadora", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Perfil local do protótipo, sem conta ou rede social.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("Privacidade")
            Text(
                PrivacyCopy,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DebugActions(
    sessionState: SessionState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDegrade: () -> Unit,
    onRecover: () -> Unit,
    onStop: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Debug")
        when (sessionState) {
            SessionState.IDLE, SessionState.STOPPED -> DebugButton("Iniciar sessão", onStart)
            SessionState.STARTED -> {
                DebugButton("Pausar", onPause)
                DebugButton("Simular queda", onDegrade)
                DebugButton("Terminar", onStop)
            }
            SessionState.PAUSED -> {
                DebugButton("Retomar", onResume)
                DebugButton("Terminar", onStop)
            }
            SessionState.DEGRADED -> {
                DebugButton("Pausar", onPause)
                DebugButton("Simular retorno", onRecover)
                DebugButton("Terminar", onStop)
            }
            SessionState.STARTING,
            SessionState.STOPPING,
            SessionState.PROCESSING,
            SessionState.READY,
            SessionState.ERROR -> DebugButton("Terminar", onStop)
        }
    }
}

private fun audioModeDescription(audioMode: AudioMode): String {
    if (audioMode == AudioMode.VOICE) return "Instruções curtas, faladas pela Olivia."
    return "Tons locais de 80 ms para orientar a correção."
}

@Composable
private fun DebugButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(20.dp),
    ) {
        Text(label)
    }
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
