package com.alfread.alfvision.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.alfread.alfvision.core.AccentColor
import com.alfread.alfvision.core.ThemeMode
import com.alfread.alfvision.data.prefs.AppSettings

private val DarkBackground = Color(0xFF0B0D12)
private val DarkSurface = Color(0xFF131722)

@Composable
fun ALFVisionTheme(
    settings: AppSettings,
    content: @Composable () -> Unit
) {
    val dark = when (settings.theme) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val primary = when (settings.accent) {
        AccentColor.BLUE -> Color(0xFF6EA8FF)
        AccentColor.PURPLE -> Color(0xFFB48CFF)
        AccentColor.CYAN -> Color(0xFF59D8FF)
        AccentColor.GREEN -> Color(0xFF72D49C)
        AccentColor.RED -> Color(0xFFFF7385)
        AccentColor.GOLD -> Color(0xFFFFC861)
    }
    val scheme = if (dark) {
        darkColorScheme(
            primary = primary,
            onPrimary = Color(0xFF08111D),
            background = DarkBackground,
            surface = DarkSurface,
            secondary = primary.copy(alpha = 0.82f)
        )
    } else {
        lightColorScheme(
            primary = primary,
            secondary = primary.copy(alpha = 0.82f)
        )
    }
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}
