package com.alfread.statusdownloader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Black = Color(0xFF000000)
private val White = Color(0xFFFFFFFF)

private val LightScheme = lightColorScheme(
    primary = Black, onPrimary = White,
    primaryContainer = Color(0xFFEAEAEA), onPrimaryContainer = Black,
    secondary = Color(0xFF2B2B2B), onSecondary = White,
    secondaryContainer = Color(0xFFE2E2E2), onSecondaryContainer = Black,
    tertiary = Color(0xFF444444), onTertiary = White,
    background = White, onBackground = Black,
    surface = White, onSurface = Black,
    surfaceVariant = Color(0xFFF0F0F0), onSurfaceVariant = Color(0xFF4A4A4A),
    surfaceTint = Black,
    inverseSurface = Black, inverseOnSurface = White, inversePrimary = White,
    error = Black, onError = White,
    errorContainer = Color(0xFFE2E2E2), onErrorContainer = Black,
    outline = Color(0xFF8A8A8A), outlineVariant = Color(0xFFD6D6D6),
    scrim = Black,
    surfaceContainerLowest = White,
    surfaceContainerLow = Color(0xFFF7F7F7),
    surfaceContainer = Color(0xFFF2F2F2),
    surfaceContainerHigh = Color(0xFFEDEDED),
    surfaceContainerHighest = Color(0xFFE6E6E6)
)

private val DarkScheme = darkColorScheme(
    primary = White, onPrimary = Black,
    primaryContainer = Color(0xFF262626), onPrimaryContainer = White,
    secondary = Color(0xFFD9D9D9), onSecondary = Black,
    secondaryContainer = Color(0xFF2E2E2E), onSecondaryContainer = White,
    tertiary = Color(0xFFBDBDBD), onTertiary = Black,
    background = Black, onBackground = White,
    surface = Black, onSurface = White,
    surfaceVariant = Color(0xFF1A1A1A), onSurfaceVariant = Color(0xFFBDBDBD),
    surfaceTint = White,
    inverseSurface = White, inverseOnSurface = Black, inversePrimary = Black,
    error = White, onError = Black,
    errorContainer = Color(0xFF2E2E2E), onErrorContainer = White,
    outline = Color(0xFF7A7A7A), outlineVariant = Color(0xFF333333),
    scrim = Black,
    surfaceContainerLowest = Black,
    surfaceContainerLow = Color(0xFF101010),
    surfaceContainer = Color(0xFF161616),
    surfaceContainerHigh = Color(0xFF1C1C1C),
    surfaceContainerHighest = Color(0xFF242424)
)

private val AppTypography = Typography(
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
)

@Composable
fun AlfreadTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = AppTypography,
        content = content
    )
}
