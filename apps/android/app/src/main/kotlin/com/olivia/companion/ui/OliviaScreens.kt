package com.olivia.companion.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.olivia.companion.GlassesBackend
import com.olivia.core.AudioMode

@Composable
fun OliviaWelcomeScreen(onContinue: () -> Unit) {
    OliviaPage {
        OliviaPanel {
            Text("Olivia", style = MaterialTheme.typography.bodyLarge)
            Text("Grava com as mãos livres", style = MaterialTheme.typography.headlineMedium)
            PanelDetail(
                "Eu oriento enquadramento, luz e horizonte por áudio enquanto você trabalha.",
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Comando de voz", style = MaterialTheme.typography.bodyLarge)
                Text("Oi, Olivia, Gravar", style = MaterialTheme.typography.headlineSmall)
            }
            PanelDetail("Frames de análise são efêmeros. Sem conta.")
        }
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text("Continuar")
        }
    }
}

@Composable
fun OliviaHomeScreen() {
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
                OliviaStat("Sessões", "0", Modifier.weight(1f))
                OliviaStat("Correções", "0", Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun OliviaRecordingScreen(backend: GlassesBackend, onOpenSettings: () -> Unit) {
    OliviaPage {
        OliviaPanel {
            Text("Comando de voz", style = MaterialTheme.typography.bodyLarge)
            Text("Oi, Olivia, Gravar", style = MaterialTheme.typography.headlineMedium)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DeviceStatus(backend)
                PanelDetail(
                    if (backend.requiresPhysicalGlasses) {
                        "É necessário conectar os óculos para gravar."
                    } else {
                        "Prévia do fluxo por voz, com óculos simulados."
                    },
                )
            }
        }
        Text(
            "Os comandos usam o microfone do celular. A orientação chega por áudio.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
fun OliviaSessionsScreen() {
    OliviaPage {
        OliviaPanel {
            Text("Seus takes", style = MaterialTheme.typography.bodyLarge)
            Text("Nenhuma sessão ainda", style = MaterialTheme.typography.headlineMedium)
            PanelDetail("Depois da gravação, suas timelines e cortes sugeridos aparecerão aqui.")
        }
    }
}

@Composable
fun OliviaSettingsScreen(onNavigateBack: () -> Unit) {
    var audioMode by rememberSaveable { mutableStateOf(AudioMode.VOICE.name) }

    OliviaPage(onNavigateBack = onNavigateBack) {
        OliviaPanel {
            SectionTitle("Modo de feedback")
            FlowRow(
                modifier = Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AudioModeOption("Voz", audioMode == AudioMode.VOICE.name) {
                    audioMode = AudioMode.VOICE.name
                }
                AudioModeOption("Sinal", audioMode == AudioMode.SIGNAL.name) {
                    audioMode = AudioMode.SIGNAL.name
                }
            }
            PanelDetail(
                if (audioMode == AudioMode.VOICE.name) {
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
                "Frames de análise são efêmeros. O áudio só é transcrito depois que a sessão termina.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OliviaPage(
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
private fun OliviaPanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.heightIn(min = 248.dp).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            content = content,
        )
    }
}

@Composable
private fun DeviceStatus(backend: GlassesBackend) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = CircleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.inverseOnSurface),
    ) {
        Text(
            if (backend.requiresPhysicalGlasses) "Óculos físicos necessários" else "Modo demonstração",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun PanelDetail(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.inverseOnSurface,
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
}

@Composable
private fun OliviaStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AudioModeOption(label: String, selected: Boolean, onClick: () -> Unit) {
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
