package com.olivia.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.olivia.companion.R
import com.olivia.companion.session.OliviaUiState
import com.olivia.companion.session.TakeSummary
import com.olivia.companion.session.audioModeLabel
import com.olivia.companion.session.confirmationLabel
import com.olivia.companion.session.connectionLabel
import com.olivia.companion.session.eventTitle
import com.olivia.companion.session.formatOffset
import com.olivia.companion.session.isConfirmed
import com.olivia.companion.session.sessionStateLabel
import com.olivia.core.AudioMode
import com.olivia.core.SessionId
import com.olivia.core.SessionState
import com.olivia.core.TimelineEvent
import com.olivia.core.TimelineEventType

private val PrivacyCopy =
    "Frames de análise são efêmeros. O áudio do celular não alimenta IA durante o take."

@Composable
fun OliviaWelcomeScreen(onGoogleLogin: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp)
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
            GoogleLoginButton(
                onClick = onGoogleLogin,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun GoogleLoginButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 56.dp),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_google),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = Color.Unspecified,
            )
            Text(stringResource(R.string.continue_with_google), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun OliviaHomeScreen(state: OliviaUiState) {
    val sessions = state.takes.count { !it.isDemo || it.events.isNotEmpty() }
    val corrections = state.takes.sumOf { it.correctionCount }
    OliviaPage {
        OliviaPanel {
            Text("Dispositivo", style = MaterialTheme.typography.bodyLarge)
            Text("Ray-Ban Meta", style = MaterialTheme.typography.headlineMedium)
        }
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            SectionTitle("Seu resumo")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                OliviaStat("Sessões", sessions.toString(), Modifier.weight(1f))
                OliviaStat("Correções", corrections.toString(), Modifier.weight(1f))
            }
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
        OliviaPanel(degraded = degraded, compact = true) {
            Text("Sessão", style = MaterialTheme.typography.bodyLarge)
            Text(sessionStateLabel(sessionState), style = MaterialTheme.typography.headlineMedium)
            Text(formatOffset(state.elapsedMs), style = MaterialTheme.typography.headlineSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusChip(connectionLabel(state.connection), inverted = !degraded)
                StatusChip(audioModeLabel(state.audioMode), inverted = !degraded)
            }
            PanelDetail(
                text = if (degraded) {
                    "A Olivia parou de ver a câmera. As marcas ficam; a gravação nativa continua."
                } else if (state.backend.requiresPhysicalGlasses) {
                    "É necessário conectar os óculos para gravar."
                } else {
                    "Prévia do fluxo, com óculos simulados. Escolha o modo antes de iniciar."
                },
                onPanel = !degraded,
            )
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
        Text(PrivacyCopy, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
fun OliviaSessionsScreen(takes: List<TakeSummary>, onOpenTake: (SessionId) -> Unit) {
    OliviaPage {
        if (takes.isEmpty()) {
            OliviaPanel {
                Text("Seus takes", style = MaterialTheme.typography.bodyLarge)
                Text("Nenhuma sessão ainda", style = MaterialTheme.typography.headlineMedium)
                PanelDetail("Depois da gravação, suas timelines e cortes sugeridos aparecerão aqui.")
            }
        } else {
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
    }
}

@Composable
fun OliviaTakeScreen(take: TakeSummary, onNavigateBack: () -> Unit) {
    OliviaPage(onNavigateBack = onNavigateBack) {
        OliviaPanel {
            Text(if (take.isDemo) "Ensaio mock" else "Revisão", style = MaterialTheme.typography.bodyLarge)
            Text(formatOffset(take.durationMs), style = MaterialTheme.typography.headlineMedium)
            PanelDetail("${take.correctionCount} correções · ${take.cutCount} cortes sugeridos")
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("Cortes sugeridos")
            if (take.cutCount == 0) {
                Text(
                    "Nenhum corte neste take. A lista aceita o ensaio mock e o pós-processamento real.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                take.events.filter { it.type == TimelineEventType.CUT_SUGGESTED }.forEach { event ->
                    TimelineRow(event)
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("Timeline")
            take.events.forEach { event -> TimelineRow(event) }
        }
    }
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
                if (audioMode == AudioMode.VOICE) {
                    "Instruções curtas, faladas pela Olivia."
                } else {
                    "Tons locais de 80 ms para orientar a correção."
                },
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
        SectionTitle("Debug mock")
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
            else -> DebugButton("Terminar", onStop)
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
