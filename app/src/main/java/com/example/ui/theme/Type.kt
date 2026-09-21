package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val PlusJakartaSansFamily = FontFamily(Font(R.font.plus_jakarta_sans))
val OutfitFamily = FontFamily(Font(R.font.outfit))
val SpaceGroteskFamily = FontFamily(Font(R.font.space_grotesk))
val JetBrainsMonoFamily = FontFamily(Font(R.font.jetbrains_mono))
val CinzelFamily = FontFamily(Font(R.font.cinzel))

enum class AppFont(val id: String, val displayName: String, val subtitle: String, val fontFamily: FontFamily) {
    PLUS_JAKARTA_SANS("PLUS_JAKARTA_SANS", "Plus Jakarta Sans", "Modern, Crisp & Balanced (Recommended)", PlusJakartaSansFamily),
    OUTFIT("OUTFIT", "Outfit", "Contemporary, Rhythmic & Sleek", OutfitFamily),
    SPACE_GROTESK("SPACE_GROTESK", "Space Grotesk", "Techno & Poweramp-Style Aesthetics", SpaceGroteskFamily),
    JETBRAINS_MONO("JETBRAINS_MONO", "JetBrains Mono", "Audiophile DSP & Studio Monospace", JetBrainsMonoFamily),
    CINZEL("CINZEL", "Cinzel", "Classical Vinyl & Elegant Typography", CinzelFamily),
    SYSTEM("SYSTEM", "System Default", "Standard Android Roboto", FontFamily.Default);

    companion object {
        fun fromId(id: String?): AppFont {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: PLUS_JAKARTA_SANS
        }
    }
}

fun createAppTypography(fontFamily: FontFamily): Typography {
    return Typography(
        displayLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            lineHeight = 38.sp,
            letterSpacing = (-0.75).sp
        ),
        displayMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
            lineHeight = 32.sp,
            letterSpacing = (-0.5).sp
        ),
        displaySmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            letterSpacing = (-0.25).sp
        ),
        headlineLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            lineHeight = 26.sp,
            letterSpacing = (-0.2).sp
        ),
        headlineMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            letterSpacing = (-0.15).sp
        ),
        headlineSmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.sp
        ),
        titleLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            lineHeight = 22.sp,
            letterSpacing = (-0.1).sp
        ),
        titleMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp
        ),
        titleSmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.1.sp
        ),
        bodyLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.2.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.2.sp
        ),
        bodySmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            letterSpacing = 0.3.sp
        ),
        labelLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.5.sp
        ),
        labelMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            letterSpacing = 0.8.sp
        ),
        labelSmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            letterSpacing = 1.0.sp
        )
    )
}

val Typography = createAppTypography(PlusJakartaSansFamily)


