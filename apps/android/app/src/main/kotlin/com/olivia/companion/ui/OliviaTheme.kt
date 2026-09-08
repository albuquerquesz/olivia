package com.olivia.companion.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object OliviaMotion {
    private val EaseOut = CubicBezierEasing(0.25f, 1f, 0.5f, 1f)
    val travel = 8.dp

    fun <T> navigation() = tween<T>(durationMillis = 220, easing = EaseOut)
    fun <T> selection() = tween<T>(durationMillis = 180, easing = EaseOut)
    fun <T> press() = tween<T>(durationMillis = 150, easing = EaseOut)
}

private val Paper = Color(0xFFFAFBFD)
private val Navy = Color(0xFF142C4C)
private val Ink = Color(0xFF151A21)
private val Mist = Color(0xFFE8EEF5)

private val OliviaLightColors = lightColorScheme(
    primary = Navy,
    onPrimary = Paper,
    primaryContainer = Mist,
    onPrimaryContainer = Navy,
    secondary = Navy,
    onSecondary = Paper,
    secondaryContainer = Mist,
    onSecondaryContainer = Navy,
    tertiary = Navy,
    onTertiary = Paper,
    tertiaryContainer = Mist,
    onTertiaryContainer = Navy,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Mist,
    onSurfaceVariant = Color(0xFF526174),
    surfaceTint = Navy,
    surfaceBright = Paper,
    surfaceDim = Mist,
    surfaceContainerLowest = Paper,
    surfaceContainerLow = Paper,
    surfaceContainer = Mist,
    surfaceContainerHigh = Mist,
    surfaceContainerHighest = Mist,
    inverseSurface = Navy,
    inverseOnSurface = Color(0xFFDCE5F0),
    inversePrimary = Paper,
    outline = Color(0xFF718198),
    outlineVariant = Color(0xFFDCE3EC),
)

private fun textStyle(size: Int, lineHeight: Int, weight: FontWeight) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = 0.sp,
)

private val OliviaTypography = Typography(
    headlineLarge = textStyle(32, 40, FontWeight.Light),
    headlineMedium = textStyle(32, 40, FontWeight.Light),
    headlineSmall = textStyle(24, 32, FontWeight.Normal),
    titleLarge = textStyle(24, 32, FontWeight.Normal),
    titleMedium = textStyle(16, 24, FontWeight.Medium),
    titleSmall = textStyle(14, 20, FontWeight.Medium),
    bodyLarge = textStyle(16, 24, FontWeight.Normal),
    bodyMedium = textStyle(16, 24, FontWeight.Normal),
    bodySmall = textStyle(14, 20, FontWeight.Normal),
    labelLarge = textStyle(14, 20, FontWeight.Medium),
    labelMedium = textStyle(12, 16, FontWeight.Medium),
    labelSmall = textStyle(12, 16, FontWeight.Medium),
)

@Composable
fun OliviaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OliviaLightColors,
        typography = OliviaTypography,
        content = content,
    )
}
