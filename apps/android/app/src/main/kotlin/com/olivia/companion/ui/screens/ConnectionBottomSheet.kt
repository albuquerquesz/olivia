package com.olivia.companion.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BluetoothDisabled
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.olivia.companion.GlassesBackend
import com.olivia.companion.session.OliviaUiState
import com.olivia.core.GlassesConnection

private data class ConnectionSheetCopy(
    val title: String,
    val message: String,
    val icon: ImageVector,
    val iconTint: Color,
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ConnectionBottomSheet(
    state: OliviaUiState,
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val copy = connectionSheetCopy(state.connection, state.backend)
    var canContinue by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        canContinue = true
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Conexão dos óculos",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = "Confira a disponibilidade antes de abrir a tela Gravar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = copy.icon,
                        contentDescription = null,
                        tint = copy.iconTint,
                        modifier = Modifier.size(24.dp),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = copy.title,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = copy.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Fonte",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = state.backend.label,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Button(
                onClick = { if (canContinue) onContinue() },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("Continuar para Gravar")
            }
        }
    }
}

@Composable
private fun connectionSheetCopy(
    connection: GlassesConnection,
    backend: GlassesBackend,
): ConnectionSheetCopy {
    val colors = MaterialTheme.colorScheme
    return when (connection) {
        GlassesConnection.CONNECTED -> ConnectionSheetCopy(
            title = if (backend.requiresPhysicalGlasses) {
                "Óculos conectados"
            } else {
                "Demonstração pronta"
            },
            message = if (backend.requiresPhysicalGlasses) {
                "A Olivia pode receber os frames dos óculos e acompanhar a sessão."
            } else {
                "O fluxo offline está pronto. Nenhum óculos físico é usado nesta demonstração."
            },
            icon = Icons.Outlined.CheckCircle,
            iconTint = colors.primary,
        )

        GlassesConnection.DISCONNECTED -> ConnectionSheetCopy(
            title = "Sem frames no momento",
            message = "A Olivia não está recebendo frames dos óculos. Esse estado continuará visível em Gravar.",
            icon = Icons.Outlined.BluetoothDisabled,
            iconTint = colors.onSurfaceVariant,
        )

        GlassesConnection.UNAVAILABLE -> ConnectionSheetCopy(
            title = "Óculos indisponíveis",
            message = if (backend.requiresPhysicalGlasses) {
                "O adapter dos óculos físicos ainda não está disponível neste ambiente."
            } else {
                "A conexão de demonstração não está disponível neste momento."
            },
            icon = Icons.Outlined.Bluetooth,
            iconTint = colors.onSurfaceVariant,
        )
    }
}
