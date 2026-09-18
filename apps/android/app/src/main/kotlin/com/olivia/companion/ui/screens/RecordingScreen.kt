package com.olivia.companion.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.olivia.companion.session.AudioFeedbackStatus
import com.olivia.companion.session.OliviaUiState
import com.olivia.companion.session.formatOffset
import com.olivia.companion.ui.OliviaPage
import com.olivia.companion.ui.OliviaPanel
import com.olivia.companion.ui.OliviaRecordingRed
import com.olivia.companion.ui.SettingsAudioModeSelector
import com.olivia.companion.ui.SettingsCard
import com.olivia.companion.ui.SettingsOverline
import com.olivia.core.AudioMode
import com.olivia.core.SessionState
import com.olivia.core.TimelineEventType
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun OliviaRecordingScreen(
    state: OliviaUiState,
    onAudioMode: (AudioMode) -> Unit,
) {
    val degraded = state.session?.state == SessionState.DEGRADED
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
                    .heightIn(min = 320.dp, max = 360.dp),
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
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontSize = 16.sp,
                                lineHeight = 20.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                }
                if (degraded || state.backend.requiresPhysicalGlasses) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(20.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.76f),
                        contentColor = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text = recordingPanelMessage(
                                degraded = degraded,
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
        WhisperTranscriptCard(
            text = whisperFeedbackText(
                state = state,
                degraded = degraded,
                live = live,
            ),
        )
        AudioAvailabilityNotice(
            mode = state.audioMode,
            status = state.audioStatus,
            message = state.audioStatusMessage,
        )
        SettingsCard {
            SettingsOverline("Modo de feedback")
            SettingsAudioModeSelector(
                audioMode = state.audioMode,
                enabled = !live,
                onAudioMode = onAudioMode,
            )
        }
    }
}

private val RecordingTimerBackground = OliviaRecordingRed
private val RecordingTimerContent = Color.White

@Composable
private fun WhisperTranscriptCard(text: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Whisper. Feedback transcrito: $text"
            },
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Outlined.Mic,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Whisper",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = "Feedback transcrito",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f),
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

private fun whisperFeedbackText(
    state: OliviaUiState,
    degraded: Boolean,
    live: Boolean,
): String {
    val latestCorrection = state.takes
        .firstOrNull { it.title == "Take ao vivo" }
        ?.events
        ?.asSequence()
        ?.filter { it.type == TimelineEventType.CORRECTION_ISSUED }
        ?.lastOrNull()
        ?.label
    if (latestCorrection != null) return latestCorrection
    if (degraded) return recordingPanelMessage(degraded = true)
    if (live) return "Aguardando o próximo feedback da Olivia."
    return "O feedback transcrito aparecerá depois que o take terminar."
}

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
private fun GlassesCameraPreview(
    elapsedMs: Long,
    degraded: Boolean,
    simulated: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val description = cameraPreviewDescription()
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
): String {
    if (degraded) {
        return "A Olivia parou de ver a câmera. As marcas ficam; a gravação nativa continua."
    }
    return "É necessário conectar os óculos para gravar."
}

private fun cameraPreviewDescription(): String =
    "Prévia da câmera dos óculos, formato vertical 720 por 1280"

private fun subjectCenter(width: Float, hasFramingIssue: Boolean): Float {
    if (hasFramingIssue) return width * 0.76f
    return width * 0.5f
}

private fun horizonOffset(hasHorizonIssue: Boolean, offset: Float): Float {
    if (hasHorizonIssue) return offset
    return 0f
}
