package com.example.ui.screens.lyrics

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.player.LyricsManager
import com.example.ui.components.poweramp.PowerampWaveformBar
import com.example.ui.theme.Poweramp_Amber
import com.example.ui.theme.Poweramp_Cyan
import com.example.ui.theme.Poweramp_Lime
import com.example.ui.theme.SoundboxTheme
import com.example.ui.viewmodel.MusicViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

data class SyncLine(
    val index: Int,
    val text: String,
    val timeMs: Long? = null
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LyricsSyncEditorScreen(
    viewModel: MusicViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val currentSong by viewModel.currentSong.collectAsState()
    val position by viewModel.currentPosition.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val songDuration by viewModel.duration.collectAsState()
    
    // Lyric Editor States
    val linesList = remember { mutableStateListOf<SyncLine>() }
    var rawInputText by remember { mutableStateOf("") }
    var isInputMode by remember { mutableStateOf(true) }
    
    // Dialog / Edit States
    var showEditDialog by remember { mutableStateOf<SyncLine?>(null) }
    var textEditValue by remember { mutableStateOf("") }
    var timeMinutesEdit by remember { mutableStateOf("") }
    var timeSecondsEdit by remember { mutableStateOf("") }
    var timeHundredthsEdit by remember { mutableStateOf("") }

    val lazyListState = rememberLazyListState()

    // Load initial lyrics if existing
    LaunchedEffect(currentSong) {
        val song = currentSong ?: return@LaunchedEffect
        val loaded = LyricsManager.loadLyrics(context, song)
        if (loaded != null && loaded.isNotBlank()) {
            val parsed = mutableListOf<SyncLine>()
            val textLines = loaded.lines()
            var indexCounter = 0
            
            for (line in textLines) {
                if (line.isBlank()) continue
                // Standard LRC timestamp: [mm:ss.xx]Text
                val match = "\\[(\\d{2}):(\\d{2})\\.(\\d{2})](.*)".toRegex().matchEntire(line)
                if (match != null) {
                    val container = match.groupValues
                    val min = container[1].toLongOrNull() ?: 0L
                    val sec = container[2].toLongOrNull() ?: 0L
                    val hund = container[3].toLongOrNull() ?: 0L
                    val totalMs = (min * 60000) + (sec * 1000) + (hund * 10)
                    val textVal = container[4].trim()
                    parsed.add(SyncLine(indexCounter++, textVal, totalMs))
                } else {
                    parsed.add(SyncLine(indexCounter++, line.trim(), null))
                }
            }
            if (parsed.isNotEmpty()) {
                linesList.clear()
                linesList.addAll(parsed)
                isInputMode = false
            }
        }
    }

    val colors = SoundboxTheme.colors

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "LRC SYNC EDITOR",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.5.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = colors.accentCyan
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.surfaceVariant)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "KARAOKE DSP",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 7.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = colors.accentLime
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary
                        )
                    }
                },
                actions = {
                    if (!isInputMode) {
                        IconButton(onClick = {
                            isInputMode = true
                            rawInputText = linesList.joinToString("\n") { it.text }
                        }) {
                            Icon(
                                Icons.Default.EditNote,
                                contentDescription = "Edit Text Lines",
                                tint = colors.accentCyan
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.topBarBackground
                )
            )
        }
    ) { innerPadding ->
        if (currentSong == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF131D2A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicOff,
                            contentDescription = "No music",
                            modifier = Modifier.size(36.dp),
                            tint = Color(0xFF5A6E85)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No track currently loaded",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Play an audio file from your library first, then return here to tag time-synced lyrics.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF7A8E9E),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            val song = currentSong!!

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // TRACK BANNER & WAVEFORM
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF0E1622),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1D2B3D))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF182638)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = Poweramp_Cyan,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${song.artist} • ${formatPositionTime(position)} / ${formatPositionTime(songDuration)}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = Poweramp_Cyan.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Interactive Waveform Seekbar
                        PowerampWaveformBar(
                            currentPosition = position,
                            duration = songDuration,
                            isPlaying = isPlaying,
                            onSeek = { targetMs -> viewModel.seekTo(targetMs) },
                            accentColor = Poweramp_Cyan,
                            seedKey = song.id.toString(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                        )
                    }
                }

                // Section 2: Input Mode vs Syncing Viewport
                if (isInputMode) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Icon(
                                Icons.Default.Subject,
                                contentDescription = null,
                                tint = Poweramp_Cyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "RAW LYRICS INPUT (PASTE LINE-BY-LINE):",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = Poweramp_Cyan
                            )
                        }

                        OutlinedTextField(
                            value = rawInputText,
                            onValueChange = { rawInputText = it },
                            placeholder = { Text("Paste song lyrics line-by-line here...", color = Color(0xFF5A6E85)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF0C131D),
                                unfocusedContainerColor = Color(0xFF0C131D),
                                focusedBorderColor = Poweramp_Cyan,
                                unfocusedBorderColor = Color(0xFF1E2C3D),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Default)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                if (rawInputText.isBlank()) {
                                    Toast.makeText(context, "Please insert lyrics text first", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val lines = rawInputText.lines()
                                    .map { it.trim() }
                                    .filter { it.isNotBlank() }
                                    .mapIndexed { index, text -> SyncLine(index, text, null) }

                                linesList.clear()
                                linesList.addAll(lines)
                                isInputMode = false

                                if (!isPlaying) {
                                    viewModel.playPause()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Poweramp_Cyan,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "START TIME SYNCING",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                } else {
                    // Active list tagging layout
                    val nextSyncIndex = linesList.indexOfFirst { it.timeMs == null }

                    LaunchedEffect(nextSyncIndex) {
                        if (nextSyncIndex >= 0 && linesList.isNotEmpty()) {
                            val scrollPos = (nextSyncIndex - 2).coerceAtLeast(0)
                            lazyListState.animateScrollToItem(scrollPos)
                        }
                    }

                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 20.dp)
                    ) {
                        itemsIndexed(linesList) { index, line ->
                            val isCurrentTagTarget = index == nextSyncIndex
                            val hasTimestamp = line.timeMs != null

                            Surface(
                                color = when {
                                    isCurrentTagTarget -> Color(0xFF00E5FF).copy(alpha = 0.12f)
                                    hasTimestamp -> Color(0xFF101B27)
                                    else -> Color(0xFF0C121B)
                                },
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isCurrentTagTarget) Poweramp_Cyan else Color(0xFF182535)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showEditDialog = line
                                        textEditValue = line.text
                                        val curTimeMs = line.timeMs ?: 0L
                                        val min = curTimeMs / 60000
                                        val sec = (curTimeMs % 60000) / 1000
                                        val hund = (curTimeMs % 1000) / 10
                                        timeMinutesEdit = String.format(Locale.US, "%02d", min)
                                        timeSecondsEdit = String.format(Locale.US, "%02d", sec)
                                        timeHundredthsEdit = String.format(Locale.US, "%02d", hund)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Timestamp Indicator
                                    Box(
                                        modifier = Modifier
                                            .width(84.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                if (hasTimestamp) Poweramp_Cyan.copy(alpha = 0.15f)
                                                else Color(0xFF15202E)
                                            )
                                            .padding(vertical = 4.dp, horizontal = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (hasTimestamp) formatLrcTimeLabel(line.timeMs!!) else "--:--.--",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp
                                            ),
                                            color = if (hasTimestamp) Poweramp_Cyan else Color(0xFF5A6E85)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Text(
                                        text = line.text,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isCurrentTagTarget) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = if (isCurrentTagTarget) Poweramp_Cyan else if (hasTimestamp) Color.White else Color(0xFF8A9CAF),
                                        modifier = Modifier.weight(1f)
                                    )

                                    if (isCurrentTagTarget) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Poweramp_Lime)
                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                "NEXT",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 8.sp,
                                                    fontFamily = FontFamily.Monospace
                                                ),
                                                color = Color.Black
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Section 3: Bottom Editor Toolbar
                    Surface(
                        color = Color(0xFF0A1019),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1A2738)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Sub-row 1: Navigation / Playback control buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { viewModel.seekTo((position - 5000).coerceAtLeast(0)) }) {
                                    Icon(
                                        Icons.Default.Replay5,
                                        contentDescription = "Back 5s",
                                        tint = Poweramp_Cyan,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }

                                Surface(
                                    onClick = { viewModel.playPause() },
                                    shape = CircleShape,
                                    color = Poweramp_Cyan,
                                    modifier = Modifier.size(46.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = "Play/Pause",
                                            tint = Color.Black,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }

                                IconButton(onClick = { viewModel.seekTo((position + 5000).coerceAtMost(songDuration)) }) {
                                    Icon(
                                        Icons.Default.Forward5,
                                        contentDescription = "Forward 5s",
                                        tint = Poweramp_Cyan,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        val lastStampedIdx = linesList.indexOfLast { it.timeMs != null }
                                        if (lastStampedIdx >= 0) {
                                            linesList[lastStampedIdx] = linesList[lastStampedIdx].copy(timeMs = null)
                                            Toast.makeText(context, "Undone Line #${lastStampedIdx + 1}", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    enabled = linesList.any { it.timeMs != null }
                                ) {
                                    Icon(
                                        Icons.Default.Undo,
                                        contentDescription = "Undo",
                                        tint = if (linesList.any { it.timeMs != null }) Poweramp_Amber else Color(0xFF3B4D60)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Sub-row 2: PRIMARY ACTION CORE BUTTON (Big prominent Timestamp trigger!)
                            Button(
                                onClick = {
                                    if (nextSyncIndex >= 0) {
                                        linesList[nextSyncIndex] = linesList[nextSyncIndex].copy(timeMs = position)
                                    } else {
                                        Toast.makeText(context, "All lines have been synced!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (nextSyncIndex >= 0) Poweramp_Cyan else Poweramp_Lime,
                                    contentColor = Color.Black
                                )
                            ) {
                                Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (nextSyncIndex >= 0) {
                                        "TAG NEXT LINE @ ${formatPositionTime(position)}"
                                    } else {
                                        "ALL LINES SYNCED (READY TO SAVE)"
                                    },
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 1.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Sub-row 3: Final confirmation (Save LRC / Cancel)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onNavigateBack,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF8A9CAF))
                                ) {
                                    Text("Discard")
                                }

                                Button(
                                    onClick = {
                                        val lrcPairs = linesList.map {
                                            val t = it.timeMs ?: 0L
                                            Pair(t, it.text)
                                        }
                                        val lrcContent = LyricsManager.generateLrcContent(lrcPairs)
                                        LyricsManager.saveLyrics(context, song, lrcContent)

                                        Toast.makeText(context, "Synced LRC file saved for ${song.title}", Toast.LENGTH_SHORT).show()

                                        scope.launch {
                                            delay(400)
                                            onNavigateBack()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF1A3048),
                                        contentColor = Poweramp_Cyan
                                    )
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Save LRC", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Line Edit Dialog Overlay
    if (showEditDialog != null) {
        val target = showEditDialog!!
        AlertDialog(
            containerColor = Color(0xFF0F1722),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFB0BEC5),
            onDismissRequest = { showEditDialog = null },
            title = {
                Text(
                    "Edit Line #${target.index + 1}",
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Poweramp_Cyan
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = textEditValue,
                        onValueChange = { textEditValue = it },
                        label = { Text("Line Text", color = Color(0xFF7A8E9E)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF141E2C),
                            unfocusedContainerColor = Color(0xFF141E2C),
                            focusedBorderColor = Poweramp_Cyan,
                            unfocusedBorderColor = Color(0xFF223245),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        "Manual Timestamp (mm : ss . xx):",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = Poweramp_Cyan
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = timeMinutesEdit,
                            onValueChange = { strMinutes -> if (strMinutes.length <= 2 && strMinutes.all { it.isDigit() }) timeMinutesEdit = strMinutes },
                            label = { Text("Min") },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF141E2C),
                                unfocusedContainerColor = Color(0xFF141E2C),
                                focusedBorderColor = Poweramp_Cyan,
                                unfocusedBorderColor = Color(0xFF223245),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontFamily = FontFamily.Monospace)
                        )
                        Text(":", style = MaterialTheme.typography.titleLarge, color = Color.White)
                        OutlinedTextField(
                            value = timeSecondsEdit,
                            onValueChange = { strSeconds -> if (strSeconds.length <= 2 && strSeconds.all { it.isDigit() }) timeSecondsEdit = strSeconds },
                            label = { Text("Sec") },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF141E2C),
                                unfocusedContainerColor = Color(0xFF141E2C),
                                focusedBorderColor = Poweramp_Cyan,
                                unfocusedBorderColor = Color(0xFF223245),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontFamily = FontFamily.Monospace)
                        )
                        Text(".", style = MaterialTheme.typography.titleLarge, color = Color.White)
                        OutlinedTextField(
                            value = timeHundredthsEdit,
                            onValueChange = { strHund -> if (strHund.length <= 2 && strHund.all { it.isDigit() }) timeHundredthsEdit = strHund },
                            label = { Text("Hund") },
                            modifier = Modifier.weight(1.2f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF141E2C),
                                unfocusedContainerColor = Color(0xFF141E2C),
                                focusedBorderColor = Poweramp_Cyan,
                                unfocusedBorderColor = Color(0xFF223245),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontFamily = FontFamily.Monospace)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val minutes = timeMinutesEdit.toLongOrNull() ?: 0L
                        val seconds = timeSecondsEdit.toLongOrNull() ?: 0L
                        val hundredths = timeHundredthsEdit.toLongOrNull() ?: 0L
                        val combinedTimeMs = (minutes * 60000) + (seconds * 1000) + (hundredths * 10)

                        val updated = target.copy(
                            text = textEditValue,
                            timeMs = if (combinedTimeMs > 0 || timeMinutesEdit.isNotEmpty()) combinedTimeMs else null
                        )

                        val listIdx = linesList.indexOfFirst { it.index == target.index }
                        if (listIdx >= 0) {
                            linesList[listIdx] = updated
                        }
                        showEditDialog = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Poweramp_Cyan)
                ) {
                    Text("Apply", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row {
                    if (target.timeMs != null) {
                        TextButton(
                            onClick = {
                                val updated = target.copy(timeMs = null)
                                val listIdx = linesList.indexOfFirst { it.index == target.index }
                                if (listIdx >= 0) {
                                    linesList[listIdx] = updated
                                }
                                showEditDialog = null
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF5252))
                        ) {
                            Text("Clear Time")
                        }
                    }
                    TextButton(
                        onClick = { showEditDialog = null },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF8A9CAF))
                    ) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}

private fun formatPositionTime(timeMs: Long): String {
    val totalSec = (timeMs / 1000).coerceAtLeast(0)
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format(Locale.US, "%02d:%02d", min, sec)
}

private fun formatLrcTimeLabel(timeMs: Long): String {
    val totalSec = (timeMs / 1000).coerceAtLeast(0)
    val min = totalSec / 60
    val sec = totalSec % 60
    val hundredths = (timeMs % 1000) / 10
    return String.format(Locale.US, "%02d:%02d.%02d", min, sec, hundredths)
}
