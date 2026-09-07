package com.olivia.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.olivia.companion.GlassesBackend
import com.olivia.core.AudioMode

@Composable
fun OliviaHomeScreen(
    backend: GlassesBackend,
) {
    OliviaPage {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Estado do dispositivo", style = MaterialTheme.typography.titleMedium)
                Text(backend.label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = if (backend.requiresPhysicalGlasses) {
                        "Óculos físicos necessários"
                    } else {
                        "Modo mock pronto para demonstração"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Text("Seu resumo", style = MaterialTheme.typography.titleLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OliviaStatCard("Sessões", "0", Modifier.weight(1f))
            OliviaStatCard("Correções", "0", Modifier.weight(1f))
        }
    }
}

@Composable
fun OliviaRecordingScreen(
    backend: GlassesBackend,
    onOpenSettings: () -> Unit,
) {
    OliviaPage {
        Text("Gravar", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "A Olivia escuta os comandos no celular e orienta por áudio.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Pronta para iniciar", style = MaterialTheme.typography.titleLarge)
                Text("Diga: ‘Oi, Olivia, Gravar’", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = if (backend.requiresPhysicalGlasses) {
                        "Aguardando conexão com os Ray-Ban Meta."
                    } else {
                        "O cenário mock está disponível."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        OutlinedButton(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Configurar áudio e dispositivo")
        }
    }
}

@Composable
fun OliviaSessionsScreen() {
    OliviaPage {
        Text("Sessões", style = MaterialTheme.typography.headlineMedium)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Nenhuma sessão ainda", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Depois da gravação, suas timelines e cortes sugeridos aparecerão aqui.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
fun OliviaSettingsScreen() {
    var audioMode by rememberSaveable { mutableStateOf(AudioMode.VOICE.name) }

    OliviaPage {
        Text("Configurações", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Ajustes essenciais para a gravação mãos-livres.",
            style = MaterialTheme.typography.bodyLarge,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Modo de feedback", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = audioMode == AudioMode.VOICE.name,
                        onClick = { audioMode = AudioMode.VOICE.name },
                        label = { Text("Voz") },
                    )
                    FilterChip(
                        selected = audioMode == AudioMode.SIGNAL.name,
                        onClick = { audioMode = AudioMode.SIGNAL.name },
                        label = { Text("Sinal") },
                    )
                }
                Text(
                    text = if (audioMode == AudioMode.VOICE.name) {
                        "A Olivia fala instruções curtas."
                    } else {
                        "A Olivia usa tons locais de 80 ms."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Meu perfil", style = MaterialTheme.typography.titleMedium)
                Text("Mariana · Tatuadora", style = MaterialTheme.typography.bodyLarge)
                Text("Perfil local do protótipo, sem conta ou rede social.", style = MaterialTheme.typography.bodyMedium)
            }
        }

        HorizontalDivider()
        Text("Privacidade", style = MaterialTheme.typography.titleMedium)
        Text(
            "Frames de análise são efêmeros. O áudio só é transcrito depois que a sessão termina.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun OliviaPage(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
private fun OliviaStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
