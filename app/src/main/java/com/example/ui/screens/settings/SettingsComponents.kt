package com.example.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.SoundboxTheme

@Composable
fun SettingsSection(
    title: String,
    sectionIcon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = SoundboxTheme.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
        ) {
            Icon(
                imageVector = sectionIcon,
                contentDescription = null,
                tint = colors.accentCyan,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                ),
                color = colors.accentCyan
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = colors.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.border)
        ) {
            Column(content = content)
        }
    }
}

@Composable
fun SettingsDivider() {
    HorizontalDivider(
        color = SoundboxTheme.colors.borderSubtle,
        thickness = 1.dp,
        modifier = Modifier.padding(horizontal = 14.dp)
    )
}
