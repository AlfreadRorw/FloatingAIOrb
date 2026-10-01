package com.alf.pet.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AlfColors = lightColorScheme(
    primary = Color(0xFF5B5BD6),
    onPrimary = Color.White,
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF1B1B25),
    surface = Color.White,
    onSurface = Color(0xFF1B1B25)
)

@Composable
fun AlfPetTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AlfColors,
        content = content
    )
}
