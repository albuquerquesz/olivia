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
    fun <T> footer() = tween<T>(durationMillis = 200, easing = EaseOut)
    fun <T> selection() = tween<T>(durationMillis = 180, easing = EaseOut)
    fun <T> press() = tween<T>(durationMillis = 150, easing = EaseOut)
}

private val Paper = Color(0xFFFAFBFD)
private val Canvas = Color(0xFFF1F3F7)
private val OliviaBlue = Color(0xFFA9D3F2)
private val Ink = Color(0xFF151A21)
private val Mist = Color(0xFFE8EEF5)
internal val OliviaBlueActionStart = Color(0xFFB8DCF4)
internal val OliviaBlueActionEnd = Color(0xFF86BCE1)
internal val OliviaBlueActionOn = Paper
internal val OliviaBlueActionShadow = Color(0x3388BFE8)
internal val OliviaRecordingRed = Color(0xFFD32F2F)
internal val OliviaStatusConnected = Color(0xFFDDF4E5)
internal val OliviaStatusConnectedOn = Color(0xFF216E45)
internal val OliviaStatusWarning = Color(0xFFFFF0CC)
internal val OliviaStatusWarningOn = Color(0xFF755B00)
internal val OliviaStatusUnavailable = Color(0xFFFDE2E1)
internal val OliviaStatusUnavailableOn = Color(0xFFA52A2A)

private val OliviaLightColors = lightColorScheme(
    primary = OliviaBlue,
    onPrimary = Ink,
    primaryContainer = Mist,
    onPrimaryContainer = Ink,
    secondary = OliviaBlue,
    onSecondary = Ink,
    secondaryContainer = Mist,
    onSecondaryContainer = Ink,
    tertiary = OliviaBlue,
    onTertiary = Ink,
    tertiaryContainer = Mist,
    onTertiaryContainer = Ink,
    background = Canvas,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Mist,
    onSurfaceVariant = Color(0xFF526174),
    surfaceTint = OliviaBlue,
    surfaceBright = Paper,
    surfaceDim = Mist,
    surfaceContainerLowest = Paper,
    surfaceContainerLow = Paper,
    surfaceContainer = Mist,
    surfaceContainerHigh = Mist,
    surfaceContainerHighest = Mist,
    inverseSurface = OliviaBlue,
    inverseOnSurface = Color(0xFF526174),
    inversePrimary = Ink,
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
