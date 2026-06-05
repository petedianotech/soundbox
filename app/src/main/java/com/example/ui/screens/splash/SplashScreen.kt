package com.example.ui.screens.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onNavigateToHome: () -> Unit) {
    var startAnims by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        startAnims = true
        delay(2200) // Elegant introductory entrance delay
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
        animationSpec = tween(1200, delayMillis = 300, easing = EaseOutCubic),
        label = "TextAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1B172E),
                        Color(0xFF141218),
                        Color(0xFF0C0A0E)
                    )
                )
            ),
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
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                )
                
                // Real Image Asset Centered
                Image(
                    painter = painterResource(id = R.drawable.soundbox_logo_1780659495671),
                    contentDescription = "Soundbox App Icon",
                    modifier = Modifier
                        .size(115.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(28.dp))
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "SOUNDBOX",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 6.sp,
                    fontSize = 24.sp
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .alpha(textAlpha)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Premium Acoustic Storage",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    letterSpacing = 1.2.sp,
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier.alpha(textAlpha)
            )

            Spacer(modifier = Modifier.height(60.dp))

            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                strokeWidth = 2.dp,
                modifier = Modifier
                    .size(24.dp)
                    .alpha(textAlpha)
            )
        }
    }
}
