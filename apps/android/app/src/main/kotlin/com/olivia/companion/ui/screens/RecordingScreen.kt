package com.olivia.companion.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.olivia.companion.session.AudioFeedbackStatus
import com.olivia.companion.session.OliviaUiState
import com.olivia.companion.session.connectionLabel
import com.olivia.companion.session.formatOffset
import com.olivia.companion.ui.AudioModeOption
import com.olivia.companion.ui.OliviaPage
import com.olivia.companion.ui.OliviaPanel
import com.olivia.companion.ui.SectionTitle
import com.olivia.core.AudioMode
import com.olivia.core.SessionState
import kotlin.math.PI
import kotlin.math.sin

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
                if (live) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 20.dp),
                        color = RecordingTimerBackground,
                        contentColor = RecordingTimerContent,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text = formatOffset(state.elapsedMs),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }
                }
                ConnectionStateBadge(
                    label = connectionLabel(state.connection),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(20.dp),
                )
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
        AudioAvailabilityNotice(
            mode = state.audioMode,
            status = state.audioStatus,
            message = state.audioStatusMessage,
        )
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

private val RecordingTimerBackground = Color(0xFFFFE4E1)
private val RecordingTimerContent = Color(0xFF8B2D2D)

@Composable
private fun AudioAvailabilityNotice(
    mode: AudioMode,
    status: AudioFeedbackStatus,
    message: String?,
) {
    if (mode != AudioMode.VOICE || status != AudioFeedbackStatus.ERROR) return
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            message ?: "Modo Voz indisponível. O Modo Sinal continua local.",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ConnectionStateBadge(label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
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
