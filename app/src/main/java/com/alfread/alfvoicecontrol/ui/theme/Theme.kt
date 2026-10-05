package com.alfread.alfvoicecontrol.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AlfDarkColorScheme = darkColorScheme(
    primary = AlfAccentBlue,
    secondary = AlfAccentPurple,
    background = AlfBackground,
    surface = AlfSurface,
    surfaceVariant = AlfSurfaceVariant,
    onPrimary = AlfTextPrimary,
    onSecondary = AlfTextPrimary,
    onBackground = AlfTextPrimary,
    onSurface = AlfTextPrimary,
    error = AlfError
)

@Composable
fun ALFVoiceControlTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AlfDarkColorScheme,
        typography = AlfTypography,
        content = content
    )
}
