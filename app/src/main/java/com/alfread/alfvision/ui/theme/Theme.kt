package com.alfread.alfvision.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alfread.alfvision.core.model.Accent
import com.alfread.alfvision.core.model.AppSettings
import com.alfread.alfvision.core.model.ThemeMode

val AlfBlue = Color(0xFF1728FF)
val AlfPurple = Color(0xFF7B4DFF)
val AlfCyan = Color(0xFF14D9FF)

/**
 * FIX (penyebab FC saat aplikasi dibuka):
 * sebelumnya memakai Color(argb.toULong()) -> itu konstruktor nilai mentah (packed) Compose,
 * bukan ARGB, sehingga color space-nya invalid dan langsung crash saat tema dibuat.
 * Color(Long) adalah konstruktor ARGB yang benar.
 */
fun Accent.color(): Color = Color(argb)

@Composable
fun isDarkTheme(settings: AppSettings): Boolean = when (settings.theme) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
}

fun brandBrush(accent: Color): Brush = Brush.linearGradient(listOf(accent, AlfCyan))

fun heroBrush(accent: Color): Brush = Brush.linearGradient(listOf(AlfBlue, accent, AlfCyan.copy(alpha = 0.85f)))

@Composable
fun ALFVisionTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = isDarkTheme(settings)
    val accent = settings.accent.color()
    val scheme = if (dark) {
        darkColorScheme(
            primary = accent,
            onPrimary = Color.White,
            secondary = AlfCyan,
            onSecondary = Color(0xFF00252D),
            tertiary = Color(0xFF72D8FF),
            background = Color(0xFF0A0C16),
            onBackground = Color(0xFFE8EAF6),
            surface = Color(0xFF10132A),
            onSurface = Color(0xFFE8EAF6),
            surfaceVariant = Color(0xFF1B1F3A),
            onSurfaceVariant = Color(0xFFA9AFCB),
            surfaceContainerLow = Color(0xFF111430),
            surfaceContainer = Color(0xFF151934),
            surfaceContainerHigh = Color(0xFF1A1E3D),
            surfaceContainerHighest = Color(0xFF222749),
            outline = Color(0xFF5C6288),
            outlineVariant = Color(0xFF2E3358),
            error = Color(0xFFFF6B7A)
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            secondary = Color(0xFF006F88),
            tertiary = Color(0xFF006F88),
            background = Color(0xFFF4F5FC),
            onBackground = Color(0xFF14172B),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF14172B),
            surfaceVariant = Color(0xFFE6E8F6),
            onSurfaceVariant = Color(0xFF50567A),
            surfaceContainerLow = Color(0xFFF8F9FE),
            surfaceContainer = Color(0xFFF1F2FB),
            surfaceContainerHigh = Color(0xFFFFFFFF),
            surfaceContainerHighest = Color(0xFFE9EBF8),
            outline = Color(0xFF8A90B3),
            outlineVariant = Color(0xFFD5D8EE),
            error = Color(0xFFC62828)
        )
    }
    val base = Typography()
    val typography = base.copy(
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold)
    )
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(32.dp)
    )
    MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes, content = content)
}
