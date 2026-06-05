package com.example.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.viewmodel.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MusicViewModel,
    onNavigateToAbout: () -> Unit
) {
    val songs by viewModel.allSongs.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val sleepTimerLeft by viewModel.sleepTimerMillis.collectAsState()
    val speed by viewModel.playbackSpeed.collectAsState()
    val pitch by viewModel.playbackPitch.collectAsState()
    val crossfade by viewModel.crossfadeDuration.collectAsState()

    var showTimerDialog by remember { mutableStateOf(false) }
    var showCrossfadeDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Storage & Scanning",
            style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        )

        SettingsRow(
            title = "Scan Media Storage",
            subtitle = if (isScanning) "Searching offline directories..." else "Index local device .mp3 audio caches",
            icon = Icons.Default.LibraryMusic,
            action = {
                if (isScanning) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Button(onClick = { viewModel.scanStorage() }) {
                        Text("Scan Now")
                    }
                }
            }
        )

        SettingsRow(
            title = "Index Database Status",
            subtitle = "${songs.size} local tracks indexed in Room Cache",
            icon = Icons.Default.Storage,
            action = {}
        )

        HorizontalDivider()

        Text(
            text = "Audio & Timer Preferences",
            style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        )

        SettingsRow(
            title = "Sleep Timer Setting",
            subtitle = if (sleepTimerLeft > 0) {
                val mins = (sleepTimerLeft / 1000) / 60
                "Ticking... $mins mins remaining to auto-pause"
            } else {
                "Not active - click to initialize"
            },
            icon = Icons.Default.Timer,
            action = {
                Button(
                    onClick = { showTimerDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                ) {
                    Text(if (sleepTimerLeft > 0) "Adjust" else "Set")
                }
            }
        )

        SettingsRow(
            title = "Crossfade Duration",
            subtitle = if (crossfade > 0) "Smooth transition: ${crossfade}s" else "Gapless playback (No fade)",
            icon = Icons.Default.CompareArrows,
            action = {
                Button(
                    onClick = { showCrossfadeDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                ) {
                    Text("Adjust")
                }
            }
        )

        SettingsRow(
            title = "Default Playback Rates",
            subtitle = "Current Velocity: ${String.format("%.2fx", speed)} | Pitch: ${String.format("%.2fx", pitch)}",
            icon = Icons.Default.Speed,
            action = {
                IconButton(onClick = { viewModel.setPlaybackRate(1.0f, 1.0f) }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset Speed")
                }
            }
        )

        HorizontalDivider()

        Text(
            text = "Security & Telemetry Isolation",
            style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        )

        SettingsRow(
            title = "100% Offline Local Sandbox",
            subtitle = "Isolated with ZERO external trackers, data harvesting, or cloud transfers.",
            icon = Icons.Default.Shield,
            action = {}
        )

        SettingsRow(
            title = "Soundbox version",
            subtitle = "v1.0.0 (2026 Edition Pro)",
            icon = Icons.Default.Info,
            action = {}
        )

        SettingsRow(
            title = "About Developer",
            subtitle = "Peter Damiano (Petediano)",
            icon = Icons.Default.Person,
            action = {
                Button(onClick = onNavigateToAbout) {
                    Text("View")
                }
            }
        )

        Spacer(modifier = Modifier.height(48.dp))
    }

    // Sleep Timer choosing modal
    if (showTimerDialog) {
        AlertDialog(
            onDismissRequest = { showTimerDialog = false },
            title = { Text("Set Sleep Timer") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Choose duration to automatically pause active background playback.")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf(5, 15, 30, 45, 60).forEach { mins ->
                            Button(
                                onClick = {
                                    viewModel.startSleepTimer(mins)
                                    showTimerDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) {
                                Text("${mins}m")
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                if (sleepTimerLeft > 0) {
                    TextButton(onClick = {
                        viewModel.stopSleepTimer()
                        showTimerDialog = false
                    }) {
                        Text("Disable Timer")
                    }
                } else {
                    TextButton(onClick = { showTimerDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    // Crossfade choosing modal
    if (showCrossfadeDialog) {
        val crossfadeOptions = listOf(0, 2, 4, 8, 12)
        AlertDialog(
            onDismissRequest = { showCrossfadeDialog = false },
            title = { Text("Crossfade Settings") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Select transition duration (in seconds) between tracks.")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        crossfadeOptions.forEach { sec ->
                            Button(
                                onClick = {
                                    viewModel.setCrossfadeDuration(sec)
                                    showCrossfadeDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (crossfade == sec) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = if (crossfade == sec) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) {
                                Text(if (sec == 0) "Off" else "${sec}s")
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showCrossfadeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SettingsRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    action: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        action()
    }
}
