package com.alfread.alfvision.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.alfread.alfvision.core.model.Accent
import com.alfread.alfvision.core.model.AppSettings
import com.alfread.alfvision.core.model.ThemeMode

@Composable
fun ALFVisionTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = when (settings.theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val accent = Color(settings.accent.argb.toULong())
    val scheme = if (dark) {
        darkColorScheme(primary = accent, secondary = accent.copy(alpha = 0.82f), tertiary = Color(0xFF72D8FF))
    } else {
        lightColorScheme(primary = accent, secondary = accent.copy(alpha = 0.82f), tertiary = Color(0xFF006F88))
    }
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}
