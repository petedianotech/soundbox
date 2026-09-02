package com.example.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.ui.viewmodel.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MusicViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAbout: () -> Unit
) {
    val songs by viewModel.allSongs.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val sleepTimerLeft by viewModel.sleepTimerMillis.collectAsState()
    val speed by viewModel.playbackSpeed.collectAsState()
    val currentTheme by viewModel.settingsManager.themeFlow.collectAsState()
    val visibleTabs by viewModel.settingsManager.visibleTabsFlow.collectAsState()

    var showTimerDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showTabsDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // PLAYBACK SECTION
            SettingsSection(title = "Playback") {
                SettingsCardRow(
                    title = "Sleep Timer",
                    subtitle = if (sleepTimerLeft > 0) "${(sleepTimerLeft / 1000) / 60} mins remaining" else "Off",
                    icon = Icons.Default.Timer,
                    onClick = { showTimerDialog = true }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsCardRow(
                    title = "Audio Qualities & Speed",
                    subtitle = "Current Velocity: ${String.format("%.2fx", speed)}",
                    icon = Icons.Default.Speed,
                    onClick = { viewModel.setPlaybackRate(1.0f, 1.0f) }
                )
            }

            // LIBRARY SECTION
            SettingsSection(title = "Library") {
                SettingsCardRow(
                    title = "Scan Storage",
                    subtitle = if (isScanning) "Searching offline directories..." else "Index local device cache",
                    icon = Icons.Default.LibraryMusic,
                    onClick = { if (!isScanning) viewModel.scanStorage() }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsCardRow(
                    title = "Indexed Tracks",
                    subtitle = "${songs.size} local tracks in cache",
                    icon = Icons.Default.Storage,
                    onClick = {}
                )
            }

            // APPEARANCE SECTION
            SettingsSection(title = "Appearance") {
                SettingsCardRow(
                    title = "Theme",
                    subtitle = when(currentTheme) {
                        "LIGHT" -> "Light"
                        "DARK" -> "Dark"
                        "MIDNIGHT" -> "Midnight Black"
                        else -> "System Default"
                    },
                    icon = Icons.Default.Palette,
                    onClick = { showThemeDialog = true }
                )
            }
            
            // NAVIGATION SECTION
            SettingsSection(title = "Navigation") {
                SettingsCardRow(
                    title = "Bottom Bar Tabs",
                    subtitle = "Customize which tabs are visible",
                    icon = Icons.Default.ViewCompact,
                    onClick = { showTabsDialog = true }
                )
            }

            // ABOUT SECTION
            SettingsSection(title = "About") {
                SettingsCardRow(
                    title = "About Developer",
                    subtitle = "Peter Damiano (Petediano)",
                    icon = Icons.Default.Person,
                    onClick = onNavigateToAbout
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsCardRow(
                    title = "App Version",
                    subtitle = "v1.0.0 (Offline Edition)",
                    icon = Icons.Default.Info,
                    onClick = {}
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Modal declarations
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
                                }
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
    
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Select Theme") },
            text = {
                Column {
                    listOf("SYSTEM" to "System Default", "LIGHT" to "Light", "DARK" to "Dark", "MIDNIGHT" to "Midnight Black").forEach { (id, name) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.settingsManager.setTheme(id)
                                    showThemeDialog = false
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentTheme == id,
                                onClick = null
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showTabsDialog) {
        AlertDialog(
            onDismissRequest = { showTabsDialog = false },
            title = { Text("Visible Navigation Tabs") },
            text = {
                Column {
                    listOf("SONGS", "ALBUMS", "ARTISTS", "GENRES", "FOLDERS", "PLAYLISTS").forEach { tab ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val isVisible = visibleTabs.contains(tab)
                                    if (isVisible && visibleTabs.size > 1) {
                                        viewModel.settingsManager.toggleTabVisibility(tab, false)
                                    } else if (!isVisible) {
                                        viewModel.settingsManager.toggleTabVisibility(tab, true)
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = visibleTabs.contains(tab),
                                onCheckedChange = null
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(tab.lowercase().replaceFirstChar { it.uppercase() })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTabsDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(content = content)
        }
    }
}

@Composable
fun SettingsCardRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
