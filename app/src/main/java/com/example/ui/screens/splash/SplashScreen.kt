package com.example.ui.screens.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.Poweramp_Cyan
import com.example.ui.theme.Poweramp_Lime
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onNavigateToHome: () -> Unit) {
    var startAnims by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        startAnims = true
        delay(2000)
        onNavigateToHome()
    }

    val scale by animateFloatAsState(
        targetValue = if (startAnims) 1f else 0.4f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "LogoScale"
    )

    val textAlpha by animateFloatAsState(
        targetValue = if (startAnims) 1f else 0f,
        animationSpec = tween(1000, delayMillis = 200, easing = EaseOutCubic),
        label = "TextAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080C13)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(160.dp)
                    .scale(scale)
            ) {
                // Background aura
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(Poweramp_Cyan.copy(alpha = 0.22f), Color.Transparent)
                            ),
                            shape = CircleShape
                        )
                )

                Image(
                    painter = painterResource(id = R.drawable.soundbox_full_icon_1780662697551),
                    contentDescription = "Soundbox App Icon",
                    modifier = Modifier
                        .size(115.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .border(1.5.dp, Poweramp_Cyan.copy(alpha = 0.6f), RoundedCornerShape(28.dp))
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "SOUNDBOX",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 6.sp,
                    fontSize = 26.sp,
                    fontFamily = FontFamily.Monospace
                ),
                color = Color.White,
                modifier = Modifier.alpha(textAlpha)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .alpha(textAlpha)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF101C2B))
                    .border(1.dp, Poweramp_Cyan.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "PRO HI-RES AUDIO ENGINE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Poweramp_Lime,
                        letterSpacing = 1.8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(54.dp))

            CircularProgressIndicator(
                color = Poweramp_Cyan,
                strokeWidth = 2.5.dp,
                modifier = Modifier
                    .size(24.dp)
                    .alpha(textAlpha)
            )
        }
    }
}
