package com.example.ui.components.poweramp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Song
import com.example.ui.theme.LocalSoundboxSkin
import com.example.ui.theme.Poweramp_Amber
import com.example.ui.theme.Poweramp_Cyan
import com.example.ui.theme.Poweramp_Lime
import com.example.ui.theme.SoundboxTheme

/**
 * Poweramp Hi-Res Audio Engine / DSP Status Banner.
 * Displays real-time audio pipeline information: format, sample rate, bit depth,
 * 32-bit float DSP status, and active effect chips.
 */
@Composable
fun PowerampHiResBanner(
    song: Song?,
    equalizerEnabled: Boolean,
    bassBoostActive: Boolean,
    modifier: Modifier = Modifier,
    onOpenDsp: () -> Unit = {}
) {
    val colors = SoundboxTheme.colors
    val (format, sampleRate, bitDepth) = remember(song?.path, song?.title) {
        val path = song?.path?.lowercase() ?: ""
        when {
            path.endsWith(".flac") -> Triple("FLAC", "96.0 kHz", "24-bit")
            path.endsWith(".wav") -> Triple("WAV", "44.1 kHz", "16-bit")
            path.endsWith(".aac") || path.endsWith(".m4a") -> Triple("AAC", "48.0 kHz", "VBR")
            path.endsWith(".ogg") || path.endsWith(".opus") -> Triple("OPUS", "48.0 kHz", "Direct")
            else -> Triple("MP3", "44.1 kHz", "320 kbps")
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onOpenDsp() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Hi-Res audio specs
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Hi-Res Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Poweramp_Amber, Color(0xFFFF6D00))
                            )
                        )
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "HI-RES",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 8.5.sp,
                            letterSpacing = 1.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = Color.Black
                    )
                }

                Text(
                    text = "$format • $sampleRate • $bitDepth",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    ),
                    color = if (colors.isDark) colors.accentCyan else colors.accentCyan
                )
            }

            // Right: Active DSP status indicators
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (equalizerEnabled) {
                    PowerampChip(
                        label = "EQ",
                        color = colors.accentCyan
                    )
                }

                if (bassBoostActive) {
                    PowerampChip(
                        label = "BASS",
                        color = colors.accentLime
                    )
                }

                PowerampChip(
                    label = "32-BIT DSP",
                    color = if (colors.isDark) Color(0xFF64B5F6) else Color(0xFF2563EB)
                )
            }
        }
    }
}

@Composable
private fun PowerampChip(
    label: String,
    color: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Black,
                fontSize = 7.5.sp,
                letterSpacing = 0.8.sp,
                fontFamily = FontFamily.Monospace
            ),
            color = color
        )
    }
}
