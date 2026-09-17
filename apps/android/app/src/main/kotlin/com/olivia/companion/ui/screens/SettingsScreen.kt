package com.olivia.companion.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.olivia.companion.ui.SettingsAudioModeSelector
import com.olivia.companion.ui.SettingsCard
import com.olivia.companion.ui.SettingsDivider
import com.olivia.companion.ui.SettingsMetricRow
import com.olivia.companion.ui.SettingsOverline
import com.olivia.core.AudioMode

@Composable
fun OliviaSettingsScreen(
    audioMode: AudioMode,
    live: Boolean,
    onAudioMode: (AudioMode) -> Unit,
) {
    OliviaPage {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp),
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
                SettingsAudioModeSelector(
                    audioMode = audioMode,
                    enabled = !live,
                    onAudioMode = onAudioMode,
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
private fun SettingsMetricValue(value: String) {
    Text(
        value,
        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Normal),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
        textAlign = TextAlign.End,
    )
}
