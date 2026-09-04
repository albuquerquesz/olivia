package com.olivia.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.olivia.companion.GlassesBackend

@Composable
fun OliviaFoundationScreen(
    backend: GlassesBackend,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Olivia",
            style = MaterialTheme.typography.displaySmall,
        )
        Text(
            text = "Companion app dos Ray-Ban Meta.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Fonte da sessão",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = backend.label,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = if (backend.requiresPhysicalGlasses) {
                        "Precisa dos óculos físicos."
                    } else {
                        "Roda sem óculos e sem o SDK do DAT."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Preview
@Composable
private fun OliviaFoundationScreenPreview() {
    OliviaTheme {
        OliviaFoundationScreen(
            backend = GlassesBackend(
                id = "mock",
                label = "Óculos simulados",
                requiresPhysicalGlasses = false,
                usesDatSdk = false,
            ),
        )
    }
}
