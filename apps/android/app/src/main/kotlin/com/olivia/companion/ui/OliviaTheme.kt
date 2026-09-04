package com.olivia.companion.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val OliviaColors = darkColorScheme(
    primary = Color(0xFF7CE0C0),
    background = Color(0xFF0F1115),
    surface = Color(0xFF181B21),
)

@Composable
fun OliviaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OliviaColors,
        content = content,
    )
}
