package com.alfread.alfdownloader.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.alfread.alfdownloader.R

/** Pilihan font aplikasi (semua berlisensi OFL, dibundel di res/font). */
data class AlfFont(val name: String, val family: FontFamily)

val AlfFonts: List<AlfFont> = listOf(
    AlfFont("Sistem", FontFamily.Default),
    AlfFont(
        "Poppins", FontFamily(
            Font(R.font.poppins_regular, FontWeight.Normal),
            Font(R.font.poppins_medium, FontWeight.Medium),
            Font(R.font.poppins_semibold, FontWeight.SemiBold),
            Font(R.font.poppins_bold, FontWeight.Bold),
            Font(R.font.poppins_extrabold, FontWeight.ExtraBold)
        )
    ),
    AlfFont("Righteous", FontFamily(Font(R.font.righteous_regular, FontWeight.Normal))),
    AlfFont("Audiowide", FontFamily(Font(R.font.audiowide_regular, FontWeight.Normal))),
    AlfFont(
        "Space Mono", FontFamily(
            Font(R.font.spacemono_regular, FontWeight.Normal),
            Font(R.font.spacemono_bold, FontWeight.Bold)
        )
    ),
    AlfFont("Bebas", FontFamily(Font(R.font.bebasneue_regular, FontWeight.Normal))),
    AlfFont("Pacifico", FontFamily(Font(R.font.pacifico_regular, FontWeight.Normal))),
    AlfFont("Lobster", FontFamily(Font(R.font.lobster_regular, FontWeight.Normal))),
    AlfFont("Pixel", FontFamily(Font(R.font.pressstart2p_regular, FontWeight.Normal)))
)

fun fontFamilyFor(index: Int): FontFamily = AlfFonts[index.coerceIn(0, AlfFonts.lastIndex)].family

/** Typography Material3 yang seluruh gayanya memakai satu keluarga font. */
fun alfTypography(family: FontFamily): Typography {
    val b = Typography()
    return Typography(
        displayLarge = b.displayLarge.copy(fontFamily = family),
        displayMedium = b.displayMedium.copy(fontFamily = family),
        displaySmall = b.displaySmall.copy(fontFamily = family),
        headlineLarge = b.headlineLarge.copy(fontFamily = family),
        headlineMedium = b.headlineMedium.copy(fontFamily = family),
        headlineSmall = b.headlineSmall.copy(fontFamily = family),
        titleLarge = b.titleLarge.copy(fontFamily = family),
        titleMedium = b.titleMedium.copy(fontFamily = family),
        titleSmall = b.titleSmall.copy(fontFamily = family),
        bodyLarge = b.bodyLarge.copy(fontFamily = family),
        bodyMedium = b.bodyMedium.copy(fontFamily = family),
        bodySmall = b.bodySmall.copy(fontFamily = family),
        labelLarge = b.labelLarge.copy(fontFamily = family),
        labelMedium = b.labelMedium.copy(fontFamily = family),
        labelSmall = b.labelSmall.copy(fontFamily = family)
    )
}
