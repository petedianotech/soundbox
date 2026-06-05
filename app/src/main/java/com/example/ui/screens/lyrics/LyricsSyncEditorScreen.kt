package com.example.ui.screens.lyrics

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Song
import com.example.player.LyricsManager
import com.example.ui.viewmodel.MusicViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    var linesList = remember { mutableStateListOf<SyncLine>() }
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
                // Try to parse standard LRC timestamp: [mm:ss.xx]Text
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
                    // Try rough fallback line parse
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "LRC Lyrics Creator",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!isInputMode) {
                        IconButton(onClick = {
                            isInputMode = true
                            // Prep input with current text lines
                            rawInputText = linesList.joinToString("\n") { it.text }
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset / Re-import")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
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
                    Icon(
                        imageVector = Icons.Default.MusicOff,
                        contentDescription = "No music",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No track currently loaded.",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Please selection option/play an offline track first, and then return to tag lyrics.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            val song = currentSong!!
            
            // Build visual page colors
            val defaultColor = MaterialTheme.colorScheme.primary
            val songColor = remember(song.title) {
                val colors = listOf(
                    Color(0xFFFF8A80), Color(0xFFFF80AB), Color(0xFFEA80FC), Color(0xFFB388FF),
                    Color(0xFF82B1FF), Color(0xFF80D8FF), Color(0xFF84FFFF), Color(0xFFA7FFEB),
                    Color(0xFFB9F6CA), Color(0xFFFFE57F), Color(0xFFFFD180), Color(0xFFFF9E80)
                )
                val idx = song.title.hashCode().coerceAtLeast(0) % colors.size
                colors[idx]
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Section 1: Song Info & Progress Header Bar
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        Brush.sweepGradient(
                                            listOf(songColor, defaultColor)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White)
                            }
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = song.artist,
                                    style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Progress slider/tracker
                        val ratio = if (songDuration > 0) position.toFloat() / songDuration else 0f
                        Slider(
                            value = ratio.coerceIn(0f, 1f),
                            onValueChange = { targetVal ->
                                val targetPosition = (targetVal * songDuration).toLong()
                                viewModel.seekTo(targetPosition)
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = songColor,
                                activeTrackColor = songColor
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatPositionTime(position),
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                text = formatPositionTime(songDuration),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Section 2: Input Mode vs Syncing Viewport
                if (isInputMode) {
                    // Paste/Txt Import view
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = "Paste or import song lyrics to begin:",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        OutlinedTextField(
                            value = rawInputText,
                            onValueChange = { rawInputText = it },
                            placeholder = { Text("Paste song lyrics line-by-line here...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Button(
                            onClick = {
                                if (rawInputText.isBlank()) {
                                    Toast.makeText(context, "Please insert lyrics content first!", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val lines = rawInputText.lines()
                                    .map { it.trim() }
                                    .filter { it.isNotBlank() }
                                    .mapIndexed { index, text -> SyncLine(index, text, null) }
                                
                                linesList.clear()
                                linesList.addAll(lines)
                                isInputMode = false
                                
                                // Auto start playing if not playing to assist workflow
                                if (!isPlaying) {
                                    viewModel.playPause()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .padding(bottom = 16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = songColor),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Start Syncing Process", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                } else {
                    // Active list tagging layout
                    // Determine which is current active line (first line without a timestamp)
                    val nextSyncIndex = linesList.indexOfFirst { it.timeMs == null }
                    
                    // Auto-scroll list to focus on next active row
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
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 40.dp)
                    ) {
                        itemsIndexed(linesList) { index, line ->
                            val isCurrentTagTarget = index == nextSyncIndex
                            val hasTimestamp = line.timeMs != null
                            
                            val containerColor = when {
                                isCurrentTagTarget -> songColor.copy(alpha = 0.16f)
                                hasTimestamp -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                else -> MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                            }
                            
                            val outlineColor = if (isCurrentTagTarget) songColor else Color.Transparent

                            Surface(
                                color = containerColor,
                                border = if (isCurrentTagTarget) ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.linearGradient(listOf(songColor, songColor))) else null,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        // Open dialog to manually adjust or delete details
                                        showEditDialog = line
                                        textEditValue = line.text
                                        val curTimeMs = line.timeMs ?: 0L
                                        val min = curTimeMs / 60000
                                        val sec = (curTimeMs % 60000) / 1000
                                        val hund = (curTimeMs % 1000) / 10
                                        timeMinutesEdit = String.format("%02d", min)
                                        timeSecondsEdit = String.format("%02d", sec)
                                        timeHundredthsEdit = String.format("%02d", hund)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Timestamp Indicator
                                    Box(
                                        modifier = Modifier
                                            .width(90.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                if (hasTimestamp) songColor.copy(alpha = 0.18f)
                                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                                            )
                                            .padding(vertical = 4.dp, horizontal = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (hasTimestamp) formatLrcTimeLabel(line.timeMs!!) else "---",
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                            color = if (hasTimestamp) songColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.width(14.dp))
                                    
                                    // Lyric line text
                                    Text(
                                        text = line.text,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = if (isCurrentTagTarget) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = if (isCurrentTagTarget) songColor else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    
                                    if (isCurrentTagTarget) {
                                        Icon(
                                            imageVector = Icons.Default.Adjust,
                                            contentDescription = "Sync Target",
                                            tint = songColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Section 3: Bottom Editor Toolbar
                    Surface(
                        tonalElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Sub-row 1: Navigation / Playback control buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Seek backward 5s
                                IconButton(onClick = { viewModel.seekTo((position - 5000).coerceAtLeast(0)) }) {
                                    Icon(Icons.Default.Replay5, contentDescription = "Back 5 seconds", modifier = Modifier.size(28.dp))
                                }

                                // Play / Pause Button
                                FloatingActionButton(
                                    onClick = { viewModel.playPause() },
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.size(52.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Play/Pause"
                                    )
                                }

                                // Seek forward 5s
                                IconButton(onClick = { viewModel.seekTo((position + 5000).coerceAtMost(songDuration)) }) {
                                    Icon(Icons.Default.Forward5, contentDescription = "Forward 5 seconds", modifier = Modifier.size(28.dp))
                                }

                                // Undo Last Timestamp Button
                                IconButton(
                                    onClick = {
                                        val lastStampedIdx = linesList.indexOfLast { it.timeMs != null }
                                        if (lastStampedIdx >= 0) {
                                            linesList[lastStampedIdx] = linesList[lastStampedIdx].copy(timeMs = null)
                                            Toast.makeText(context, "Undone timestamp for Line ${lastStampedIdx + 1}", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    enabled = linesList.any { it.timeMs != null }
                                ) {
                                    Icon(Icons.Default.Undo, contentDescription = "Undo Last Timestamp")
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Sub-row 2: PRIMARY ACTION CORE BUTTON (Big prominent Timestamp trigger!)
                            Button(
                                onClick = {
                                    if (nextSyncIndex >= 0) {
                                        // Tag first untagged line with the active player position
                                        linesList[nextSyncIndex] = linesList[nextSyncIndex].copy(timeMs = position)
                                    } else {
                                        Toast.makeText(context, "All lines have been synced!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(58.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = songColor
                                )
                            ) {
                                Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (nextSyncIndex >= 0) {
                                        "Tag Next Line @ ${formatPositionTime(position)}"
                                    } else {
                                        "All Lines Tagged Successfully!"
                                    },
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Sub-row 3: Final confirmation (Save LRC / Cancel)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onNavigateBack,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Discard Changes")
                                }

                                Button(
                                    onClick = {
                                        // Generate and save standard LRC file format 
                                        val lrcPairs = linesList.map { 
                                            val t = it.timeMs ?: 0L // Default plain lines to 0
                                            Pair(t, it.text)
                                        }
                                        val lrcContent = LyricsManager.generateLrcContent(lrcPairs)
                                        
                                        LyricsManager.saveLyrics(context, song, lrcContent)
                                        
                                        Toast.makeText(context, "Successfully created and synced LRC file for ${song.title}!", Toast.LENGTH_LONG).show()
                                        
                                        // Pop back with small delay
                                        scope.launch {
                                            delay(500)
                                            onNavigateBack()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Save LRC File")
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
            onDismissRequest = { showEditDialog = null },
            title = { Text("Edit Line #${target.index + 1}") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = textEditValue,
                        onValueChange = { textEditValue = it },
                        label = { Text("Line Text") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Text("Manual Timestamp adjustment (min : sec . ms):", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = timeMinutesEdit,
                            onValueChange = { strMinutes -> if (strMinutes.length <= 2 && strMinutes.all { it.isDigit() }) timeMinutesEdit = strMinutes },
                            label = { Text("Min") },
                            modifier = Modifier.weight(1f),
                            textStyle = androidx.compose.ui.text.TextStyle(textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        )
                        Text(":", style = MaterialTheme.typography.titleLarge)
                        OutlinedTextField(
                            value = timeSecondsEdit,
                            onValueChange = { strSeconds -> if (strSeconds.length <= 2 && strSeconds.all { it.isDigit() }) timeSecondsEdit = strSeconds },
                            label = { Text("Sec") },
                            modifier = Modifier.weight(1f),
                            textStyle = androidx.compose.ui.text.TextStyle(textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        )
                        Text(".", style = MaterialTheme.typography.titleLarge)
                        OutlinedTextField(
                            value = timeHundredthsEdit,
                            onValueChange = { strHund -> if (strHund.length <= 2 && strHund.all { it.isDigit() }) timeHundredthsEdit = strHund },
                            label = { Text("Hund") },
                            modifier = Modifier.weight(1.2f),
                            textStyle = androidx.compose.ui.text.TextStyle(textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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
                        
                        // Replace the corresponding line in linesList
                        val listIdx = linesList.indexOfFirst { it.index == target.index }
                        if (listIdx >= 0) {
                            linesList[listIdx] = updated
                        }
                        showEditDialog = null
                    }
                ) {
                    Text("Apply Changes")
                }
            },
            dismissButton = {
                Row {
                    if (target.timeMs != null) {
                        TextButton(
                            onClick = {
                                // Delete the timestamp
                                val updated = target.copy(timeMs = null)
                                val listIdx = linesList.indexOfFirst { it.index == target.index }
                                if (listIdx >= 0) {
                                    linesList[listIdx] = updated
                                }
                                showEditDialog = null
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Clear Time")
                        }
                    }
                    TextButton(onClick = { showEditDialog = null }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}

// Convert position ms to mm:ss label
private fun formatPositionTime(timeMs: Long): String {
    val totalSec = timeMs / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format("%02d:%02d", min, sec)
}

// Convert timestamp ms to standard mm:ss.xx label for LRC items
private fun formatLrcTimeLabel(timeMs: Long): String {
    val totalSec = timeMs / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    val hundredths = (timeMs % 1000) / 10
    return String.format("%02d:%02d.%02d", min, sec, hundredths)
}
