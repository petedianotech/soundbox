package com.example.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Poweramp_Amber
import com.example.ui.theme.Poweramp_Cyan
import com.example.ui.theme.Poweramp_Lime
import com.example.ui.theme.SoundboxTheme
import com.example.ui.viewmodel.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MusicViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToEqualizer: () -> Unit = {},
    onNavigateToInsights: () -> Unit = {},
    onNavigateToCleaner: () -> Unit = {}
) {
    val songs by viewModel.allSongs.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val sleepTimerLeft by viewModel.sleepTimerMillis.collectAsState()
    val currentTheme by viewModel.settingsManager.themeFlow.collectAsState()
    val currentFont by viewModel.settingsManager.fontFlow.collectAsState()
    val visibleTabs by viewModel.settingsManager.visibleTabsFlow.collectAsState()
    val gaplessEnabled by viewModel.settingsManager.gaplessPlayback.collectAsState()
    val crossfadeEnabled by viewModel.settingsManager.crossfadeEnabled.collectAsState()
    val crossfadeDurationMs by viewModel.settingsManager.crossfadeDurationMs.collectAsState()
    val replayGain by viewModel.settingsManager.replayGainMode.collectAsState()
    val hiResEngine by viewModel.settingsManager.hiResAudioEngine.collectAsState()
    val keepScreenOn by viewModel.settingsManager.keepScreenOn.collectAsState()
    val hapticFeedback by viewModel.settingsManager.hapticFeedback.collectAsState()
    val visualizerStyle by viewModel.settingsManager.visualizerStyle.collectAsState()
    val visualizerEnabled by viewModel.settingsManager.visualizerEnabled.collectAsState()
    val autoPauseHeadphone by viewModel.settingsManager.autoPauseOnHeadphoneUnplug.collectAsState()
    val autoResumeHeadphone by viewModel.settingsManager.autoResumeOnHeadphonePlug.collectAsState()

    var showTimerDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            SettingsSection(title = "AUDIO ENGINE", sectionIcon = Icons.Default.Speaker) {
                SettingsCardRow(title = "Equalizer & DSP", subtitle = "10-band graphic EQ, bass boost, virtualizer", icon = Icons.Default.Equalizer, badge = "PRO", badgeColor = Poweramp_Lime, onClick = onNavigateToEqualizer)
                SettingsDivider()
                SettingsToggleRow(title = "Hi-Res Audio Engine (32-bit)", subtitle = "Direct hardware floating-point audio output pipeline", icon = Icons.Default.HighQuality, checked = hiResEngine, onCheckedChange = { viewModel.settingsManager.setHiResAudioEngine(it) })
                SettingsDivider()
                SettingsCardRow(title = "ReplayGain Normalization", subtitle = when (replayGain) { "TRACK" -> "Track Peak Normalization (-14 LUFS)"; "ALBUM" -> "Album Peak Normalization"; else -> "Disabled" }, icon = Icons.Default.VolumeUp, onClick = { })
            }

            SettingsSection(title = "Playback & Sleep Timer", sectionIcon = Icons.Default.PlayCircle) {
                SettingsToggleRow(title = "Gapless Playback", subtitle = "Seamless track transition without acoustic pauses", icon = Icons.Default.SyncAlt, checked = gaplessEnabled, onCheckedChange = { viewModel.settingsManager.setGaplessPlayback(it) })
                SettingsDivider()
                SettingsToggleRow(
                    title = "Crossfade",
                    subtitle = if (crossfadeEnabled) "Poweramp-style equal-power overlap (${crossfadeDurationMs / 1000}s)" else "Smooth equal-power fade between tracks",
                    icon = Icons.Default.SwapHoriz,
                    checked = crossfadeEnabled,
                    onCheckedChange = { viewModel.settingsManager.setCrossfadeEnabled(it) }
                )
                if (crossfadeEnabled) {
                    SettingsDivider()
                    SettingsCardRow(
                        title = "Crossfade Duration",
                        subtitle = "${crossfadeDurationMs / 1000} seconds overlap",
                        icon = Icons.Default.Timelapse,
                        badge = "${crossfadeDurationMs / 1000}s",
                        badgeColor = Poweramp_Cyan,
                        onClick = {
                            val options = listOf(2000, 3000, 5000, 8000, 10000, 12000)
                            val idx = options.indexOf(crossfadeDurationMs).let { if (it < 0) 2 else it }
                            val next = options[(idx + 1) % options.size]
                            viewModel.settingsManager.setCrossfadeDurationMs(next)
                        }
                    )
                }
                SettingsDivider()
                SettingsCardRow(title = "Sleep Timer", subtitle = if (sleepTimerLeft > 0) "${(sleepTimerLeft / 1000) / 60}m remaining until pause" else "Timer inactive", icon = Icons.Default.Timer, badge = if (sleepTimerLeft > 0) "ACTIVE" else null, badgeColor = Poweramp_Amber, onClick = { showTimerDialog = true })
            }

            SettingsSection(title = "LOOK & FEEL (SKIN & VISUALIZER)", sectionIcon = Icons.Default.Palette) {
                SettingsCardRow(title = "App Theme", subtitle = currentTheme, icon = Icons.Default.ColorLens, onClick = { })
                SettingsDivider()
                SettingsCardRow(title = "Font Style", subtitle = currentFont, icon = Icons.Default.TextFields, onClick = { })
                SettingsDivider()
                SettingsToggleRow(title = "Keep Screen Awake in Player", subtitle = "Prevent display timeout while viewing Now Playing lyrics", icon = Icons.Default.WbSunny, checked = keepScreenOn, onCheckedChange = { viewModel.settingsManager.setKeepScreenOn(it) })
            }

            SettingsSection(title = "HEADSET & AUTOMATION", sectionIcon = Icons.Default.Headphones) {
                SettingsToggleRow(title = "Auto-Pause on Unplug", subtitle = "Pause playback automatically when headphones or Bluetooth disconnect", icon = Icons.Default.HeadsetOff, checked = autoPauseHeadphone, onCheckedChange = { viewModel.settingsManager.setAutoPauseOnHeadphoneUnplug(it) })
                SettingsDivider()
                SettingsToggleRow(title = "Auto-Resume on Plug", subtitle = "Resume playback when connecting headphones or Bluetooth device", icon = Icons.Default.Headset, checked = autoResumeHeadphone, onCheckedChange = { viewModel.settingsManager.setAutoResumeOnHeadphonePlug(it) })
            }

            SettingsSection(title = "NAVIGATION BAR", sectionIcon = Icons.Default.ViewCompact) {
                SettingsCardRow(title = "Visible Navigation Tabs", subtitle = "${visibleTabs.size} tabs active", icon = Icons.Default.DashboardCustomize, onClick = { })
            }

            SettingsSection(title = "SMART LIBRARY & ANALYTICS", sectionIcon = Icons.Default.Insights) {
                SettingsCardRow(title = "Soundbox Insights & Stats", subtitle = "Playtime analytics, top artists, genres & audiophile badges", icon = Icons.Default.BarChart, badge = "ANALYTICS", badgeColor = Poweramp_Cyan, onClick = onNavigateToInsights)
                SettingsDivider()
                SettingsCardRow(title = "Duplicate & Low-Bitrate Cleaner", subtitle = "Automated scanner to reclaim storage & clean low-quality audio", icon = Icons.Default.CleaningServices, badge = "CLEANER", badgeColor = Poweramp_Amber, onClick = onNavigateToCleaner)
            }

            SettingsSection(title = "MUSIC LIBRARY & STORAGE", sectionIcon = Icons.Default.FolderOpen) {
                SettingsCardRow(title = "Rescan Device Storage", subtitle = if (isScanning) "Deep scanning media storage..." else "Scan internal & SD card directories", icon = Icons.Default.Refresh, badge = if (isScanning) "SCANNING" else null, badgeColor = Poweramp_Cyan, onClick = { if (!isScanning) viewModel.scanStorage() })
                SettingsDivider()
                SettingsCardRow(title = "Indexed Track Database", subtitle = "${songs.size} high fidelity audio files registered", icon = Icons.Default.LibraryMusic, onClick = { })
            }

            SettingsSection(title = "ABOUT SOUNDBOX", sectionIcon = Icons.Default.Info) {
                SettingsCardRow(title = "Developer & Credits", subtitle = "Peter Damiano (Petediano)", icon = Icons.Default.Person, onClick = onNavigateToAbout)
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }

    if (showTimerDialog) {
        com.example.ui.components.SleepTimerDialog(viewModel = viewModel, onDismiss = { showTimerDialog = false })
    }
}
