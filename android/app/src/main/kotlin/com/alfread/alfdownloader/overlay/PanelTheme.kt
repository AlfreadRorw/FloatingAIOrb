package com.alfread.alfdownloader.overlay

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Tema tampilan bar & panel mengambang. */
data class PanelTheme(
    val name: String,
    val bg: List<Color>,          // gradasi latar panel (atas → bawah)
    val border: List<Color>,      // gradasi garis tepi
    val surface: Color,           // kartu/kolom di dalam panel
    val text: Color,
    val muted: Color,
    val accent: Color,
    val barColors: List<Color>,   // warna bar tepi layar
    val light: Boolean = false
) {
    fun bgBrush(alpha: Float): Brush {
        val cs = bg.map { it.copy(alpha = (it.alpha * alpha).coerceIn(0f, 1f)) }
        return if (cs.size == 1) Brush.verticalGradient(listOf(cs[0], cs[0])) else Brush.linearGradient(cs)
    }
    fun borderBrush(): Brush =
        if (border.size == 1) Brush.verticalGradient(listOf(border[0], border[0])) else Brush.linearGradient(border)
    fun barBrush(): Brush =
        if (barColors.size == 1) Brush.verticalGradient(listOf(barColors[0], barColors[0])) else Brush.verticalGradient(barColors)
}

val PanelThemes = listOf(
    PanelTheme(
        "Obsidian",
        bg = listOf(Color(0xFF0F0F12)), border = listOf(Color(0xFF26262C)),
        surface = Color(0xFF17171C), text = Color(0xFFF4F4F6), muted = Color(0xFF8B8B95),
        accent = Color(0xFFFFFFFF), barColors = listOf(Color(0xFFFFFFFF), Color(0xFFD9D9E0))
    ),
    PanelTheme(
        "Kaca",
        bg = listOf(Color(0x33FFFFFF), Color(0x14FFFFFF)), border = listOf(Color(0x66FFFFFF), Color(0x1AFFFFFF)),
        surface = Color(0x1FFFFFFF), text = Color(0xFFFFFFFF), muted = Color(0xCCDDDDE6),
        accent = Color(0xFF8FD3FF), barColors = listOf(Color(0xCCFFFFFF), Color(0x66FFFFFF))
    ),
    PanelTheme(
        "Neon",
        bg = listOf(Color(0xFF0A0614), Color(0xFF160A2E)), border = listOf(Color(0xFF00F0FF), Color(0xFFFF2BD6)),
        surface = Color(0xFF1B1038), text = Color(0xFFF3EEFF), muted = Color(0xFFA79BCB),
        accent = Color(0xFF00F0FF), barColors = listOf(Color(0xFF00F0FF), Color(0xFFFF2BD6))
    ),
    PanelTheme(
        "Sunset",
        bg = listOf(Color(0xFF2D1B4E), Color(0xFF5B2A57), Color(0xFF8A3B4D)), border = listOf(Color(0xFFFFB36B), Color(0xFFFF6B8B)),
        surface = Color(0x33000000), text = Color(0xFFFFF3E8), muted = Color(0xFFE6B9B0),
        accent = Color(0xFFFFB36B), barColors = listOf(Color(0xFFFFB36B), Color(0xFFFF6B8B))
    ),
    PanelTheme(
        "Aurora",
        bg = listOf(Color(0xFF0B2B26), Color(0xFF123D4A), Color(0xFF1B2A5A)), border = listOf(Color(0xFF5CE0A0), Color(0xFF8FD3FF)),
        surface = Color(0x33000000), text = Color(0xFFE9FFF5), muted = Color(0xFF9CC9BE),
        accent = Color(0xFF5CE0A0), barColors = listOf(Color(0xFF5CE0A0), Color(0xFF8FD3FF))
    ),
    PanelTheme(
        "Sakura",
        bg = listOf(Color(0xFFFFF4F7), Color(0xFFFFE0EA)), border = listOf(Color(0xFFFFB3C9), Color(0xFFFF7A9C)),
        surface = Color(0xFFFFFFFF), text = Color(0xFF2A1620), muted = Color(0xFF8A6A75),
        accent = Color(0xFFE91E63), barColors = listOf(Color(0xFFFF7A9C), Color(0xFFE91E63)), light = true
    ),
    PanelTheme(
        "AMOLED",
        bg = listOf(Color(0xFF000000)), border = listOf(Color(0xFF2A2A2A)),
        surface = Color(0xFF0D0D0D), text = Color(0xFFFFFFFF), muted = Color(0xFF7A7A7A),
        accent = Color(0xFFB69CFF), barColors = listOf(Color(0xFFB69CFF), Color(0xFF7A5CFF))
    )
)

/** Tema aktif untuk seluruh isi panel (dengan aksen yang sudah diputuskan). */
val LocalPanel = compositionLocalOf { PanelThemes[0] }
