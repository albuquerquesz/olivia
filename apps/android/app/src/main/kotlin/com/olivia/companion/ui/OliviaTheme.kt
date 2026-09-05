package com.olivia.companion.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val OliviaLightColors = lightColorScheme(
    primary = Color(0xFF006B57),
    onPrimary = Color.White,
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF1A1C1A),
    surface = Color.White,
    onSurface = Color(0xFF1A1C1A),
)

@Composable
fun OliviaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OliviaLightColors,
        content = content,
    )
}
