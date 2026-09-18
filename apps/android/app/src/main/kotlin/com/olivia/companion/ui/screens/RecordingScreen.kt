package com.olivia.companion.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.nio.ByteBuffer
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.olivia.core.FrameFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.olivia.companion.session.AudioFeedbackStatus
import com.olivia.companion.session.LiveGuidanceStatus
import com.olivia.companion.session.LivePreviewFrame
import com.olivia.companion.session.OliviaUiState
import com.olivia.companion.session.connectionLabel
import com.olivia.companion.session.formatOffset
import com.olivia.companion.session.guidanceStatusLabel
import com.olivia.companion.session.sessionStatusLabel
import com.olivia.companion.ui.AudioModeOption
import com.olivia.companion.ui.OliviaPage
import com.olivia.companion.ui.OliviaPanel
import com.olivia.companion.ui.OliviaRecordingRed
import com.olivia.companion.ui.PanelDetail
import com.olivia.companion.ui.SectionTitle
import com.olivia.core.AudioMode
import com.olivia.core.DegradationCause
import com.olivia.core.Direction
import com.olivia.core.GlassesConnection
import com.olivia.core.SessionState
import com.olivia.core.TimelineEventType
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState

@Composable
fun OliviaRecordingScreen(
    state: OliviaUiState,
    onNavigateBack: () -> Unit,
    onOpenVoiceSettings: () -> Unit = {},
    onConnectGlasses: () -> Unit = {},
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
    BackHandler(enabled = live, onBack = onNavigateBack)
    OliviaPage(
        onNavigateBack = onNavigateBack,
        backButtonVisible = true,
        verticalSpacing = 16.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OliviaPanel(
                degraded = degraded,
                compact = true,
                contentPadding = PaddingValues(0.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 320.dp, max = 420.dp),
                ) {
                    GlassesCameraPreview(
                        elapsedMs = state.elapsedMs,
                        degraded = DegradationCause.CAPTURE_UNAVAILABLE in session?.degradationCauses.orEmpty(),
                        simulated = !state.backend.requiresPhysicalGlasses,
                        actualStreamExpected = state.backend.usesDatSdk,
                        preview = state.livePreview,
                        guidanceDirection = state.guidanceDirection,
                        guidanceStatus = state.guidanceStatus,
                        modifier = Modifier.fillMaxSize(),
                    )
                    ConnectionStateBadge(
                        label = recordingConnectionLabel(state),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(20.dp),
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
                                    session = session,
                                    connection = state.connection,
                                    requiresPhysicalGlasses = state.backend.requiresPhysicalGlasses,
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
        }
        AudioAvailabilityNotice(
            mode = state.audioMode,
            status = state.audioStatus,
            message = state.audioStatusMessage,
        )
        PanelDetail(
            when (state.voiceState) {
                com.olivia.companion.session.VoiceCommandState.LISTENING_FOR_WAKE_WORD ->
                    "Diga: Oi, Olivia. Depois: gravar, pausar, retomar ou terminar."
                com.olivia.companion.session.VoiceCommandState.LISTENING_FOR_COMMAND ->
                    "Ouvindo o comando no celular…"
                com.olivia.companion.session.VoiceCommandState.UNAVAILABLE ->
                    "Comandos locais de voz indisponíveis neste aparelho ou configuração."
                com.olivia.companion.session.VoiceCommandState.ERROR ->
                    "Não foi possível ouvir o comando."
                else -> "Comandos de voz inativos."
            },
            onPanel = false,
        )
        if (state.voiceState == com.olivia.companion.session.VoiceCommandState.UNAVAILABLE) {
            OutlinedButton(
                onClick = onOpenVoiceSettings,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                Text("Configurar comandos de voz", modifier = Modifier.weight(1f))
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 8.dp).size(20.dp),
                )
            }
        }
        if (state.backend.requiresPhysicalGlasses &&
            (session == null || sessionState == SessionState.ERROR)
        ) {
            OutlinedButton(
                onClick = onConnectGlasses,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                Text("Conectar Olivia no Meta AI", modifier = Modifier.weight(1f))
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 8.dp).size(20.dp),
                )
            }
        }
        if (!state.backend.requiresPhysicalGlasses || com.olivia.companion.BuildConfig.DEBUG) {
            DebugActions(
                sessionState = sessionState,
                onStart = onStart,
                onPause = onPause,
                onResume = onResume,
                onDegrade = onDegrade,
                onRecover = onRecover,
                onStop = onStop,
                working = state.working,
                simulationAllowed = !state.backend.requiresPhysicalGlasses,
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
                contentDescription = "Olivia no alto-falante. Transcrição: $text"
            },
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(MaterialTheme.colorScheme.onPrimary, CircleShape),
                )
                Text(
                    text = "OLIVIA NO ALTO-FALANTE",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                    ),
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f),
                )
            }
            Text(
                text = text,
                minLines = 2,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 18.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
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
    if (degraded) {
        return recordingPanelMessage(
            session = state.session,
            connection = state.connection,
            requiresPhysicalGlasses = state.backend.requiresPhysicalGlasses,
        )
    }
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
    actualStreamExpected: Boolean,
    preview: LivePreviewFrame?,
    guidanceDirection: Direction?,
    guidanceStatus: LiveGuidanceStatus,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val description = cameraPreviewDescription(simulated, actualStreamExpected, preview != null)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .semantics { contentDescription = description },
    ) {
        if (simulated && preview == null) {
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
        if (preview != null) {
            LivePreviewImage(preview)
        }
        if (preview == null && !simulated) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = colors.surfaceVariant,
                contentColor = colors.onSurfaceVariant,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        if (actualStreamExpected) "Aguardando frame do DAT…"
                        else "Prévia de vídeo indisponível",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        LivePreviewOverlay(
            direction = guidanceDirection,
            status = guidanceStatus,
            modifier = Modifier.fillMaxSize(),
        )
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

@Composable
private fun LivePreviewImage(preview: LivePreviewFrame) {
    if (preview.frame.format == FrameFormat.NV21) {
        val context = LocalContext.current
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val view = remember(context) { Nv21PreviewView(context) }
        DisposableEffect(view, lifecycle) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> view.onResume()
                    Lifecycle.Event.ON_PAUSE -> { view.clear(); view.onPause() }
                    else -> Unit
                }
            }
            lifecycle.addObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) view.onResume()
            onDispose { lifecycle.removeObserver(observer); view.clear(); view.onPause() }
        }
        AndroidView(factory = { view }, update = { it.submit(preview.frame) }, modifier = Modifier.fillMaxSize())
        return
    }
    val image: ImageBitmap? by produceState<ImageBitmap?>(initialValue = null, preview.frame.stamp) {
        value = withContext(Dispatchers.Default) {
            val frame = preview.frame
            when (frame.format) {
                FrameFormat.JPEG -> BitmapFactory.decodeByteArray(frame.bytes, 0, frame.bytes.size)
                FrameFormat.RGBA_8888 -> Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.ARGB_8888)
                    .apply { copyPixelsFromBuffer(ByteBuffer.wrap(frame.bytes)) }
                FrameFormat.NV21 -> null
            }?.asImageBitmap()
        }
    }
    image?.let {
        Image(
            bitmap = it,
            contentDescription = "Prévia ao vivo do stream da câmera dos óculos",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun LivePreviewOverlay(
    direction: Direction?,
    status: LiveGuidanceStatus,
    modifier: Modifier = Modifier,
) {
    if (direction == null || status !in setOf(
            LiveGuidanceStatus.TARGET_FOUND,
            LiveGuidanceStatus.GUIDANCE_PENDING,
            LiveGuidanceStatus.GUIDANCE_SENT,
        )
    ) return
    Canvas(modifier = modifier) {
        val red = Color(0xFFE53935).copy(alpha = 0.78f)
        val edge = 72.dp.toPx()
        val center = Offset(size.width / 2f, size.height / 2f)
        val stroke = 6.dp.toPx()
        when (direction) {
            Direction.TURN_LEFT,
            Direction.LEVEL_HORIZON_LEFT -> {
                drawRect(red.copy(alpha = 0.18f), size = Size(edge, size.height))
                drawLine(red, Offset(edge - 20.dp.toPx(), center.y), Offset(20.dp.toPx(), center.y), stroke, StrokeCap.Round)
                drawLine(red, Offset(20.dp.toPx(), center.y), Offset(42.dp.toPx(), center.y - 20.dp.toPx()), stroke, StrokeCap.Round)
                drawLine(red, Offset(20.dp.toPx(), center.y), Offset(42.dp.toPx(), center.y + 20.dp.toPx()), stroke, StrokeCap.Round)
            }
            Direction.TURN_RIGHT,
            Direction.LEVEL_HORIZON_RIGHT -> {
                drawRect(red.copy(alpha = 0.18f), topLeft = Offset(size.width - edge, 0f), size = Size(edge, size.height))
                drawLine(red, Offset(size.width - edge + 20.dp.toPx(), center.y), Offset(size.width - 20.dp.toPx(), center.y), stroke, StrokeCap.Round)
                drawLine(red, Offset(size.width - 20.dp.toPx(), center.y), Offset(size.width - 42.dp.toPx(), center.y - 20.dp.toPx()), stroke, StrokeCap.Round)
                drawLine(red, Offset(size.width - 20.dp.toPx(), center.y), Offset(size.width - 42.dp.toPx(), center.y + 20.dp.toPx()), stroke, StrokeCap.Round)
            }
            Direction.RAISE -> drawLine(red, Offset(center.x, size.height - 36.dp.toPx()), Offset(center.x, 36.dp.toPx()), stroke, StrokeCap.Round)
            Direction.LOWER -> drawLine(red, Offset(center.x, 36.dp.toPx()), Offset(center.x, size.height - 36.dp.toPx()), stroke, StrokeCap.Round)
            Direction.MOVE_CLOSER,
            Direction.MOVE_BACK,
            Direction.FACE_THE_LIGHT,
            Direction.AVOID_BACKLIGHT,
            Direction.LEAVE_THE_SHADOW -> {
                drawRoundRect(
                    red,
                    topLeft = Offset(size.width * .18f, size.height * .18f),
                    size = Size(size.width * .64f, size.height * .64f),
                    cornerRadius = CornerRadius(22.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
                )
            }
        }
    }
}

private fun recordingPanelMessage(
    session: com.olivia.core.SessionSnapshot?,
    connection: GlassesConnection,
    requiresPhysicalGlasses: Boolean,
): String {
    if (session?.state == SessionState.DEGRADED) return sessionStatusLabel(session) + ". As marcas foram preservadas."
    if (requiresPhysicalGlasses) {
        if (session == null) return "Diga: Oi, Olivia, gravar para iniciar o DAT."
        if (session.state == SessionState.STOPPED) return "Sessão encerrada. As marcas foram preservadas."
        if (session.state == SessionState.PAUSED) return "Orientação pausada. Diga: Oi, Olivia, retomar."
        if (session.state == SessionState.ERROR) {
            return "Não foi possível iniciar o DAT. Toque em Conectar Olivia no Meta AI."
        }
        if (session.state == SessionState.STARTING) return "Conectando aos óculos…"
        if (connection == GlassesConnection.CONNECTED) return "Recebendo a câmera dos óculos."
        return "Aguardando o primeiro frame do DAT…"
    }
    return "A sessão orienta por áudio. O vídeo nativo é importado após o take."
}

private fun recordingConnectionLabel(state: OliviaUiState): String {
    if (state.backend.requiresPhysicalGlasses && state.session == null) return "Aguardando início"
    if (state.session?.state == SessionState.STARTING) return "Conectando aos óculos"
    if (state.session?.state == SessionState.STOPPED) return "Sessão encerrada"
    if (state.session?.state == SessionState.PAUSED) return "Sessão pausada"
    return connectionLabel(state.connection)
}

private fun cameraPreviewDescription(
    simulated: Boolean,
    actualStreamExpected: Boolean,
    hasFrame: Boolean,
): String {
    if (hasFrame) return "Prévia ao vivo do stream da câmera dos óculos"
    if (actualStreamExpected) return "Aguardando frame do DAT"
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
    working: Boolean,
    simulationAllowed: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Debug")
        if (working) {
            Text("Aguarde a operação em andamento…")
            return@Column
        }
        when (sessionState) {
            SessionState.IDLE, SessionState.STOPPED, SessionState.READY, SessionState.ERROR -> DebugButton("Iniciar sessão", onStart)
            SessionState.STARTED -> {
                DebugButton("Pausar", onPause)
                if (simulationAllowed) DebugButton("Simular queda", onDegrade)
                DebugButton("Terminar", onStop)
            }
            SessionState.PAUSED -> {
                DebugButton("Retomar", onResume)
                DebugButton("Terminar", onStop)
            }
            SessionState.DEGRADED -> {
                DebugButton("Pausar", onPause)
                if (simulationAllowed) DebugButton("Simular retorno", onRecover)
                DebugButton("Terminar", onStop)
            }
            SessionState.STARTING,
            SessionState.STOPPING,
            SessionState.PROCESSING -> Text("Aguarde a operação em andamento…")
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
