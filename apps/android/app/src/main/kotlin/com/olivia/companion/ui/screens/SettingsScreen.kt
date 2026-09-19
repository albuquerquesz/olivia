package com.olivia.companion.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.olivia.companion.ui.OliviaPage
import com.olivia.companion.ui.SettingsAudioModeTabs
import com.olivia.companion.ui.SettingsCard
import com.olivia.companion.ui.SettingsDivider
import com.olivia.companion.ui.SettingsMetricRow
import com.olivia.companion.ui.SettingsOverline
import com.olivia.core.AudioMode
import com.olivia.core.AnalysisMode

@Composable
fun OliviaSettingsScreen(
    audioMode: AudioMode,
    analysisMode: AnalysisMode,
    live: Boolean,
    onAudioMode: (AudioMode) -> Unit,
    onAnalysisMode: (AnalysisMode) -> Unit,
) {
    OliviaPage {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                "Ajustes",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.95f),
                ),
                modifier = Modifier.semantics { heading() },
            )
            SettingsCard {
                SettingsOverline("Voz da Olivia")
                SettingsAudioModeTabs(
                    audioMode = audioMode,
                    enabled = !live,
                    onAudioMode = onAudioMode,
                )
            }
            SettingsCard {
                SettingsOverline("Privacidade da análise")
                AnalysisModeTabs(
                    mode = analysisMode,
                    enabled = !live,
                    onMode = onAnalysisMode,
                )
                Text(
                    if (analysisMode == AnalysisMode.AUTOMATIC) {
                        "Envia partes efêmeras do vídeo para modelos de IA do Google. A anonimização é automática e a qualidade é melhor."
                    } else {
                        "Mantém a análise no celular, sem enviar frames. A privacidade é maior, mas a qualidade pode ser menor."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SettingsCard {
                SettingsOverline("Bateria e análise")
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsMetricRow(
                        title = "Amostragem",
                        description = "Um frame a cada 2 s para poupar bateria",
                    ) {
                        SettingsMetricValue("0,5 fps")
                    }
                    SettingsDivider()
                    SettingsMetricRow(
                        title = "Cooldown de correção",
                        description = "Evita orientações muito próximas",
                    ) {
                        SettingsMetricValue("4 s")
                    }
                    SettingsDivider()
                    SettingsMetricRow(
                        title = "Watchdog de sessão",
                        description = "Após 4 s sem frame, a sessão fica em modo reduzido",
                    ) {
                        Switch(
                            checked = true,
                            onCheckedChange = null,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.surface,
                                checkedTrackColor = MaterialTheme.colorScheme.onSurface,
                                checkedBorderColor = MaterialTheme.colorScheme.onSurface,
                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surface,
                                uncheckedBorderColor = MaterialTheme.colorScheme.onSurface,
                                disabledCheckedThumbColor = MaterialTheme.colorScheme.surface,
                                disabledCheckedTrackColor = MaterialTheme.colorScheme.onSurface,
                                disabledCheckedBorderColor = MaterialTheme.colorScheme.onSurface,
                                disabledUncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                                disabledUncheckedTrackColor = MaterialTheme.colorScheme.surface,
                                disabledUncheckedBorderColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalysisModeTabs(
    mode: AnalysisMode,
    enabled: Boolean,
    onMode: (AnalysisMode) -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        androidx.compose.material3.FilterChip(
            selected = mode == AnalysisMode.PRIVACY,
            onClick = { onMode(AnalysisMode.PRIVACY) },
            enabled = enabled,
            label = { Text("Privacidade") },
            modifier = Modifier.weight(1f),
        )
        androidx.compose.material3.FilterChip(
            selected = mode == AnalysisMode.AUTOMATIC,
            onClick = { onMode(AnalysisMode.AUTOMATIC) },
            enabled = enabled,
            label = { Text("Automático") },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SettingsMetricValue(value: String) {
    Text(
        value,
        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Normal),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
        textAlign = TextAlign.End,
    )
}
