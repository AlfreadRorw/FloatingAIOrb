package com.alfread.alflauncher.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Dark = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    background = Color.Transparent,
    surface = Color(0xFF151515),
    onSurface = Color.White
)

private val Light = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    background = Color.Transparent,
    surface = Color.White,
    onSurface = Color.Black
)

@Composable
fun AlfTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
