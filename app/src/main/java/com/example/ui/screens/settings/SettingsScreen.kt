package com.example.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Poweramp_Amber
import com.example.ui.theme.Poweramp_Cyan
import com.example.ui.theme.Poweramp_Lime
import com.example.ui.viewmodel.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MusicViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToEqualizer: () -> Unit = {}
) {
    val songs by viewModel.allSongs.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val sleepTimerLeft by viewModel.sleepTimerMillis.collectAsState()
    val speed by viewModel.playbackSpeed.collectAsState()
    val currentTheme by viewModel.settingsManager.themeFlow.collectAsState()
    val visibleTabs by viewModel.settingsManager.visibleTabsFlow.collectAsState()

    val crossfadeSec by viewModel.settingsManager.crossfadeSeconds.collectAsState()
    val gaplessEnabled by viewModel.settingsManager.gaplessPlayback.collectAsState()
    val replayGain by viewModel.settingsManager.replayGainMode.collectAsState()
    val hiResEngine by viewModel.settingsManager.hiResAudioEngine.collectAsState()
    val keepScreenOn by viewModel.settingsManager.keepScreenOn.collectAsState()
    val hapticFeedback by viewModel.settingsManager.hapticFeedback.collectAsState()
    val visualizerStyle by viewModel.settingsManager.visualizerStyle.collectAsState()
    val eqEnabled by viewModel.equalizerEnabled.collectAsState()

    var showTimerDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showTabsDialog by remember { mutableStateOf(false) }
    var showCrossfadeDialog by remember { mutableStateOf(false) }
    var showReplayGainDialog by remember { mutableStateOf(false) }
    var showVisualizerDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color(0xFF070A0F),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "SETTINGS",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = Poweramp_Cyan
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF14202E))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "SOUNDBOX PRO",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = Poweramp_Amber
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF090D14)
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // HERO AUDIO ENGINE STATUS CARD
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF0F1722),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2D42))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(Poweramp_Cyan.copy(alpha = 0.3f), Color.Transparent)
                                )
                            )
                            .border(1.5.dp, Poweramp_Cyan, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = Poweramp_Cyan,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Soundbox Audio Engine v3.0",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.3.sp
                            ),
                            color = Color.White
                        )
                        Text(
                            text = "32-bit Float DSP • Direct Volume Control (DVC)",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = Poweramp_Cyan.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // 1. AUDIO ENGINE & DSP SECTION
            SettingsSection(title = "AUDIO ENGINE & DSP", sectionIcon = Icons.Default.Tune) {
                SettingsCardRow(
                    title = "Graphic Equalizer & Tone FX",
                    subtitle = if (eqEnabled) "10-Band Equalizer is Active" else "Bypassed / Flat Response",
                    icon = Icons.Default.Equalizer,
                    badge = if (eqEnabled) "ON" else "OFF",
                    badgeColor = if (eqEnabled) Poweramp_Lime else Color(0xFF5A697D),
                    onClick = onNavigateToEqualizer
                )
                SettingsDivider()
                SettingsToggleRow(
                    title = "Hi-Res Audio Engine (32-bit)",
                    subtitle = "Direct hardware floating-point audio output pipeline",
                    icon = Icons.Default.HighQuality,
                    checked = hiResEngine,
                    onCheckedChange = { viewModel.settingsManager.setHiResAudioEngine(it) }
                )
                SettingsDivider()
                SettingsCardRow(
                    title = "ReplayGain Normalization",
                    subtitle = when (replayGain) {
                        "TRACK" -> "Track Peak Normalization (-14 LUFS)"
                        "ALBUM" -> "Album Peak Normalization"
                        else -> "Disabled"
                    },
                    icon = Icons.Default.VolumeUp,
                    onClick = { showReplayGainDialog = true }
                )
            }

            // 2. PLAYBACK & TRANSITIONS SECTION
            SettingsSection(title = "PLAYBACK & TRANSITIONS", sectionIcon = Icons.Default.PlayCircle) {
                SettingsToggleRow(
                    title = "Gapless Playback",
                    subtitle = "Seamless track transition without acoustic pauses",
                    icon = Icons.Default.SyncAlt,
                    checked = gaplessEnabled,
                    onCheckedChange = { viewModel.settingsManager.setGaplessPlayback(it) }
                )
                SettingsDivider()
                SettingsCardRow(
                    title = "Crossfade Duration",
                    subtitle = if (crossfadeSec > 0) "$crossfadeSec seconds crossfade" else "Instant Track Cut (Off)",
                    icon = Icons.Default.LinearScale,
                    onClick = { showCrossfadeDialog = true }
                )
                SettingsDivider()
                SettingsCardRow(
                    title = "Sleep Timer",
                    subtitle = if (sleepTimerLeft > 0) "${(sleepTimerLeft / 1000) / 60}m remaining until pause" else "Timer inactive",
                    icon = Icons.Default.Timer,
                    badge = if (sleepTimerLeft > 0) "ACTIVE" else null,
                    badgeColor = Poweramp_Amber,
                    onClick = { showTimerDialog = true }
                )
                SettingsDivider()
                SettingsCardRow(
                    title = "Playback Speed & Tempo",
                    subtitle = "Current Velocity: ${String.format("%.2fx", speed)}",
                    icon = Icons.Default.Speed,
                    onClick = { viewModel.setPlaybackRate(1.0f, 1.0f) }
                )
            }

            // 3. LOOK & FEEL (SKINS & VISUALIZER)
            SettingsSection(title = "LOOK & FEEL (SKIN & THEME)", sectionIcon = Icons.Default.Palette) {
                SettingsCardRow(
                    title = "App Skin Theme",
                    subtitle = when (currentTheme) {
                        "MIDNIGHT" -> "Midnight Black (Obsidian)"
                        "DARK" -> "Dark Titanium Pro"
                        "LIGHT" -> "Clean Studio Light"
                        else -> "System Default (Auto)"
                    },
                    icon = Icons.Default.ColorLens,
                    onClick = { showThemeDialog = true }
                )
                SettingsDivider()
                SettingsCardRow(
                    title = "Audio Visualizer Renderer",
                    subtitle = when (visualizerStyle) {
                        "SPECTRUM" -> "32-Band Neon Spectrum"
                        "OSCILLOSCOPE" -> "Oscilloscope Sine Wave"
                        else -> "Real-time FFT Frequency Bars"
                    },
                    icon = Icons.Default.BarChart,
                    onClick = { showVisualizerDialog = true }
                )
                SettingsDivider()
                SettingsToggleRow(
                    title = "Haptic Knob Feedback",
                    subtitle = "Tactile vibration when rotating tone & equalizer knobs",
                    icon = Icons.Default.Vibration,
                    checked = hapticFeedback,
                    onCheckedChange = { viewModel.settingsManager.setHapticFeedback(it) }
                )
                SettingsDivider()
                SettingsToggleRow(
                    title = "Keep Screen Awake in Player",
                    subtitle = "Prevent display timeout while viewing Now Playing lyrics",
                    icon = Icons.Default.WbSunny,
                    checked = keepScreenOn,
                    onCheckedChange = { viewModel.settingsManager.setKeepScreenOn(it) }
                )
            }

            // 4. NAVIGATION TABS
            SettingsSection(title = "NAVIGATION BAR", sectionIcon = Icons.Default.ViewCompact) {
                SettingsCardRow(
                    title = "Visible Navigation Tabs",
                    subtitle = "${visibleTabs.size} tabs active (${visibleTabs.joinToString(", ") { it.take(4) }})",
                    icon = Icons.Default.DashboardCustomize,
                    onClick = { showTabsDialog = true }
                )
            }

            // 5. LIBRARY SCANNER
            SettingsSection(title = "MUSIC LIBRARY & STORAGE", sectionIcon = Icons.Default.FolderOpen) {
                SettingsCardRow(
                    title = "Rescan Device Storage",
                    subtitle = if (isScanning) "Deep scanning media storage..." else "Scan internal & SD card directories",
                    icon = Icons.Default.Refresh,
                    badge = if (isScanning) "SCANNING" else null,
                    badgeColor = Poweramp_Cyan,
                    onClick = { if (!isScanning) viewModel.scanStorage() }
                )
                SettingsDivider()
                SettingsCardRow(
                    title = "Indexed Track Database",
                    subtitle = "${songs.size} high fidelity audio files registered",
                    icon = Icons.Default.LibraryMusic,
                    onClick = {}
                )
            }

            // 6. ABOUT
            SettingsSection(title = "ABOUT SOUNDBOX", sectionIcon = Icons.Default.Info) {
                SettingsCardRow(
                    title = "Developer & Credits",
                    subtitle = "Peter Damiano (Petediano)",
                    icon = Icons.Default.Person,
                    onClick = onNavigateToAbout
                )
                SettingsDivider()
                SettingsCardRow(
                    title = "App Architecture",
                    subtitle = "Soundbox v1.0.0 (Kotlin + Jetpack Compose + Media3)",
                    icon = Icons.Default.Code,
                    onClick = {}
                )
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }

    // MODAL DIALOGS
    if (showTimerDialog) {
        AlertDialog(
            containerColor = Color(0xFF101622),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFB0BEC5),
            onDismissRequest = { showTimerDialog = false },
            title = { Text("Set Sleep Timer", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Select a countdown duration to smoothly fade out and pause playback.")
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
                                    containerColor = Color(0xFF1B283B),
                                    contentColor = Poweramp_Cyan
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("${mins}m", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                if (sleepTimerLeft > 0) {
                    TextButton(
                        onClick = {
                            viewModel.stopSleepTimer()
                            showTimerDialog = false
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF5252))
                    ) {
                        Text("Disable Timer")
                    }
                } else {
                    TextButton(
                        onClick = { showTimerDialog = false },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                    ) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    if (showCrossfadeDialog) {
        AlertDialog(
            containerColor = Color(0xFF101622),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFB0BEC5),
            onDismissRequest = { showCrossfadeDialog = false },
            title = { Text("Crossfade Duration", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    listOf(0 to "Instant Cut (0s - Off)", 2 to "2 Seconds", 4 to "4 Seconds", 6 to "6 Seconds", 8 to "8 Seconds").forEach { (sec, label) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.settingsManager.setCrossfadeSeconds(sec)
                                    showCrossfadeDialog = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = crossfadeSec == sec,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = Poweramp_Cyan)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(label, color = if (crossfadeSec == sec) Poweramp_Cyan else Color.White)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showCrossfadeDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = Poweramp_Cyan)
                ) {
                    Text("Done")
                }
            }
        )
    }

    if (showReplayGainDialog) {
        AlertDialog(
            containerColor = Color(0xFF101622),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFB0BEC5),
            onDismissRequest = { showReplayGainDialog = false },
            title = { Text("ReplayGain Mode", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    listOf(
                        "OFF" to "Disabled (Raw Volume)",
                        "TRACK" to "Track Gain (Uniform Track Loudness)",
                        "ALBUM" to "Album Gain (Preserves Album Dynamics)"
                    ).forEach { (mode, label) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.settingsManager.setReplayGainMode(mode)
                                    showReplayGainDialog = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = replayGain == mode,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = Poweramp_Cyan)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(label, color = if (replayGain == mode) Poweramp_Cyan else Color.White)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showReplayGainDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = Poweramp_Cyan)
                ) {
                    Text("Close")
                }
            }
        )
    }

    if (showVisualizerDialog) {
        AlertDialog(
            containerColor = Color(0xFF101622),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFB0BEC5),
            onDismissRequest = { showVisualizerDialog = false },
            title = { Text("Visualizer Style", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    listOf(
                        "WAVEFORM" to "Real-time FFT Frequency Bars",
                        "SPECTRUM" to "32-Band Neon Spectrum",
                        "OSCILLOSCOPE" to "Smooth Oscilloscope Sine Wave"
                    ).forEach { (style, label) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.settingsManager.setVisualizerStyle(style)
                                    showVisualizerDialog = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = visualizerStyle == style,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = Poweramp_Cyan)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(label, color = if (visualizerStyle == style) Poweramp_Cyan else Color.White)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showVisualizerDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = Poweramp_Cyan)
                ) {
                    Text("Close")
                }
            }
        )
    }

    if (showThemeDialog) {
        AlertDialog(
            containerColor = Color(0xFF101622),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFB0BEC5),
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Select App Skin Theme", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    listOf(
                        "MIDNIGHT" to "Midnight Black (Obsidian AMOLED)",
                        "DARK" to "Dark Titanium Pro (Default)",
                        "LIGHT" to "Clean Studio Light",
                        "SYSTEM" to "System Auto"
                    ).forEach { (id, name) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.settingsManager.setTheme(id)
                                    showThemeDialog = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentTheme == id,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = Poweramp_Cyan)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(name, color = if (currentTheme == id) Poweramp_Cyan else Color.White)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showThemeDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = Poweramp_Cyan)
                ) {
                    Text("Close")
                }
            }
        )
    }

    if (showTabsDialog) {
        AlertDialog(
            containerColor = Color(0xFF101622),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFB0BEC5),
            onDismissRequest = { showTabsDialog = false },
            title = { Text("Visible Navigation Tabs", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    listOf("SONGS", "ALBUMS", "ARTISTS", "GENRES", "FOLDERS", "PLAYLISTS").forEach { tab ->
                        val isVisible = visibleTabs.contains(tab)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (isVisible && visibleTabs.size > 1) {
                                        viewModel.settingsManager.toggleTabVisibility(tab, false)
                                    } else if (!isVisible) {
                                        viewModel.settingsManager.toggleTabVisibility(tab, true)
                                    }
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isVisible,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Poweramp_Cyan,
                                    checkmarkColor = Color.Black
                                )
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                tab.lowercase().replaceFirstChar { it.uppercase() },
                                color = if (isVisible) Color.White else Color(0xFF7A8A9E),
                                fontWeight = if (isVisible) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showTabsDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = Poweramp_Cyan)
                ) {
                    Text("Done")
                }
            }
        )
    }
}

@Composable
fun SettingsSection(
    title: String,
    sectionIcon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
        ) {
            Icon(
                imageVector = sectionIcon,
                contentDescription = null,
                tint = Poweramp_Cyan,
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
                color = Poweramp_Cyan
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF0F1521),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1B283A))
        ) {
            Column(content = content)
        }
    }
}

@Composable
fun SettingsDivider() {
    HorizontalDivider(
        color = Color(0xFF172230),
        thickness = 1.dp,
        modifier = Modifier.padding(horizontal = 14.dp)
    )
}

@Composable
fun SettingsCardRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    badge: String? = null,
    badgeColor: Color = Poweramp_Cyan,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF162130)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Poweramp_Cyan,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = Color.White
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF7D8E9E)
            )
        }
        if (badge != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(badgeColor)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 8.5.sp,
                        letterSpacing = 0.5.sp
                    ),
                    color = Color.Black
                )
            }
        }
    }
}

@Composable
fun SettingsToggleRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF162130)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Poweramp_Cyan,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = Color.White
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF7D8E9E)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = Poweramp_Cyan,
                uncheckedThumbColor = Color(0xFF7D8E9E),
                uncheckedTrackColor = Color(0xFF172230)
            )
        )
    }
}
