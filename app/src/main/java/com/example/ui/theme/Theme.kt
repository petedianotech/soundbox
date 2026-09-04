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
    background = Color(0xFF0C1017),
    surface = Color(0xFF131924),
    surfaceVariant = Color(0xFF192230),
    surfaceElevated = Color(0xFF202B3C),
    border = Color(0xFF243044),
    borderSubtle = Color(0xFF182232),
    textPrimary = Color(0xFFF1F5F9),
    textSecondary = Color(0xFF94A3B8),
    textMuted = Color(0xFF64748B),
    accentCyan = Color(0xFF38BDF8),
    accentAmber = Color(0xFFF59E0B),
    accentLime = Color(0xFF10B981),
    topBarBackground = Color(0xFF0C1017),
    miniPlayerBackground = Color(0xFF131924),
    dialogBackground = Color(0xFF192230)
)

val SoundboxLightSkin = SoundboxSkinColors(
    isDark = false,
    background = Color(0xFFF8FAFC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF1F5F9),
    surfaceElevated = Color(0xFFFFFFFF),
    border = Color(0xFFE2E8F0),
    borderSubtle = Color(0xFFEDF2F7),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF475569),
    textMuted = Color(0xFF64748B),
    accentCyan = Color(0xFF1D4ED8),
    accentAmber = Color(0xFFD97706),
    accentLime = Color(0xFF059669),
    topBarBackground = Color(0xFFF8FAFC),
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
    primary = Color(0xFF1D4ED8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = Color(0xFF475569),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = Color(0xFF1E293B),
    tertiary = Color(0xFF0D9488),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF082F49),
    primaryContainer = Color(0xFF0369A1),
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = Color(0xFF94A3B8),
    onSecondary = Color(0xFF0F172A),
    secondaryContainer = Color(0xFF334155),
    onSecondaryContainer = Color(0xFFE2E8F0),
    tertiary = Color(0xFF10B981),
    onTertiary = Color(0xFF022C22),
    background = Color(0xFF0C1017),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF111722),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF182232),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF26354A),
    outlineVariant = Color(0xFF1A2536)
)

@Composable
fun MyApplicationTheme(
    themeConfig: String = "DARK",
    content: @Composable () -> Unit,
) {
    val skin = when (themeConfig) {
        "LIGHT" -> SoundboxLightSkin
        else -> SoundboxDarkSkin
    }

    val colorScheme = when (themeConfig) {
        "LIGHT" -> LightColors
        else -> DarkColors
    }

    CompositionLocalProvider(LocalSoundboxSkin provides skin) {
        MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
    }
}
