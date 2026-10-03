package com.alfread.alfdownloader.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

object Ink {
    val Bg = Color(0xFF050506)
    val Surface = Color(0xFF0F0F12)
    val Surface2 = Color(0xFF16161A)
    val Surface3 = Color(0xFF202026)
    val Line = Color(0xFF26262C)
    val Text = Color(0xFFF4F4F6)
    val Muted = Color(0xFF8B8B95)
    val Danger = Color(0xFFFF6B6B)
    val Success = Color(0xFF5CE0A0)
}

data class AccentOption(val name: String, val color: Color)

val AccentOptions = listOf(
    AccentOption("Mono", Color(0xFFFFFFFF)),
    AccentOption("Ice", Color(0xFF8FD3FF)),
    AccentOption("Mint", Color(0xFF5CE0A0)),
    AccentOption("Amber", Color(0xFFFFC857)),
    AccentOption("Rose", Color(0xFFFF7A9C)),
    AccentOption("Violet", Color(0xFFB69CFF))
)

val LocalAccent = compositionLocalOf { Color.White }

enum class Tab { DOWNLOAD, LIBRARY, SETTINGS }

@Composable
fun AlfTheme(accent: Color, content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = accent, onPrimary = Color.Black,
        secondary = accent, onSecondary = Color.Black,
        background = Ink.Bg, onBackground = Ink.Text,
        surface = Ink.Surface, onSurface = Ink.Text,
        surfaceVariant = Ink.Surface2, onSurfaceVariant = Ink.Muted,
        outline = Ink.Line
    )
    CompositionLocalProvider(LocalAccent provides accent) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
