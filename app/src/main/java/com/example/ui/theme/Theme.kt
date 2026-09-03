package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

data class SoundboxSkinColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val surfaceElevated: Color,
    val border: Color,
    val borderSubtle: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accentCyan: Color,
    val accentAmber: Color,
    val accentLime: Color,
    val topBarBackground: Color,
    val miniPlayerBackground: Color,
    val dialogBackground: Color
)

val SoundboxDarkSkin = SoundboxSkinColors(
    isDark = true,
    background = Color(0xFF080C13),
    surface = Color(0xFF0E1622),
    surfaceVariant = Color(0xFF162130),
    surfaceElevated = Color(0xFF1D2A3D),
    border = Color(0xFF1E2D40),
    borderSubtle = Color(0xFF141F2C),
    textPrimary = Color(0xFFF0F4F8),
    textSecondary = Color(0xFF8E9EB5),
    textMuted = Color(0xFF5A6E85),
    accentCyan = Color(0xFF00E5FF),
    accentAmber = Color(0xFFFFB300),
    accentLime = Color(0xFF00E676),
    topBarBackground = Color(0xFF090D14),
    miniPlayerBackground = Color(0xFF0D1520),
    dialogBackground = Color(0xFF101622)
)

val SoundboxMidnightSkin = SoundboxSkinColors(
    isDark = true,
    background = Color(0xFF000000),
    surface = Color(0xFF050505),
    surfaceVariant = Color(0xFF101010),
    surfaceElevated = Color(0xFF181818),
    border = Color(0xFF262626),
    borderSubtle = Color(0xFF141414),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFA5A5A5),
    textMuted = Color(0xFF6E6E6E),
    accentCyan = Color(0xFF00E5FF),
    accentAmber = Color(0xFFFFB300),
    accentLime = Color(0xFF00E676),
    topBarBackground = Color(0xFF000000),
    miniPlayerBackground = Color(0xFF080808),
    dialogBackground = Color(0xFF0C0C0C)
)

val SoundboxLightSkin = SoundboxSkinColors(
    isDark = false,
    background = Color(0xFFF1F5F9),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE2E8F0),
    surfaceElevated = Color(0xFFF8FAFC),
    border = Color(0xFFCBD5E1),
    borderSubtle = Color(0xFFE2E8F0),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF334155),
    textMuted = Color(0xFF64748B),
    accentCyan = Color(0xFF00838F),
    accentAmber = Color(0xFFD97706),
    accentLime = Color(0xFF16A34A),
    topBarBackground = Color(0xFFFFFFFF),
    miniPlayerBackground = Color(0xFFFFFFFF),
    dialogBackground = Color(0xFFFFFFFF)
)

val LocalSoundboxSkin = staticCompositionLocalOf { SoundboxDarkSkin }

object SoundboxTheme {
    val colors: SoundboxSkinColors
        @Composable
        get() = LocalSoundboxSkin.current
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF00838F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB2EBF2),
    onPrimaryContainer = Color(0xFF002022),
    secondary = Color(0xFF4C626A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCEE6F0),
    onSecondaryContainer = Color(0xFF061E26),
    tertiary = Color(0xFFFF9100),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF1F5F9),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF334155),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF00E5FF),
    onPrimary = Color(0xFF001F24),
    primaryContainer = Color(0xFF004D54),
    onPrimaryContainer = Color(0xFF80F2FF),
    secondary = Color(0xFFFFB300),
    onSecondary = Color(0xFF452B00),
    tertiary = Color(0xFF00E676),
    onTertiary = Color(0xFF003919),
    background = Color(0xFF080C13),
    onBackground = Color(0xFFF0F4F8),
    surface = Color(0xFF0E1622),
    onSurface = Color(0xFFF0F4F8),
    surfaceVariant = Color(0xFF162130),
    onSurfaceVariant = Color(0xFF8E9EB5),
    outline = Color(0xFF1E2D40),
    outlineVariant = Color(0xFF141F2C)
)

private val MidnightColors = darkColorScheme(
    primary = Color(0xFF00E5FF),
    onPrimary = Color(0xFF001F24),
    primaryContainer = Color(0xFF004D54),
    onPrimaryContainer = Color(0xFF80F2FF),
    secondary = Color(0xFFFFB300),
    onSecondary = Color(0xFF452B00),
    tertiary = Color(0xFF00E676),
    onTertiary = Color(0xFF003919),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF050505),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF101010),
    onSurfaceVariant = Color(0xFFA5A5A5),
    outline = Color(0xFF262626),
    outlineVariant = Color(0xFF141414)
)

@Composable
fun MyApplicationTheme(
    themeConfig: String = "SYSTEM",
    content: @Composable () -> Unit,
) {
    val isSystemDark = isSystemInDarkTheme()

    val skin = when (themeConfig) {
        "LIGHT" -> SoundboxLightSkin
        "MIDNIGHT" -> SoundboxMidnightSkin
        "DARK" -> SoundboxDarkSkin
        else -> if (isSystemDark) SoundboxDarkSkin else SoundboxLightSkin
    }

    val colorScheme = when (themeConfig) {
        "LIGHT" -> LightColors
        "MIDNIGHT" -> MidnightColors
        "DARK" -> DarkColors
        else -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val context = LocalContext.current
                if (isSystemDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (isSystemDark) DarkColors else LightColors
            }
        }
    }

    CompositionLocalProvider(LocalSoundboxSkin provides skin) {
        MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
    }
}
