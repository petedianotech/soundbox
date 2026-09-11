package com.example.ui.screens.lyrics

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Song
import com.example.player.LyricsManager
import com.example.ui.components.ArtworkThumbnail
import com.example.ui.components.poweramp.PowerampWaveformBar
import com.example.ui.theme.Poweramp_Amber
import com.example.ui.theme.Poweramp_Cyan
import com.example.ui.theme.Poweramp_Lime
import com.example.ui.theme.SoundboxTheme
import com.example.ui.viewmodel.MusicViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

data class SyncLine(
    val index: Int,
    val text: String,
    val timeMs: Long? = null
)

enum class LyricsEditorMode(val label: String, val iconName: String) {
    SYNC_STUDIO("Sync Studio", "Tune"),
    LIVE_PREVIEW("Karaoke Test", "Mic"),
    TEXT_EDITOR("LRC & Text", "EditNote")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSyncEditorScreen(
    viewModel: MusicViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val currentSong by viewModel.currentSong.collectAsState()
    val position by viewModel.currentPosition.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val songDuration by viewModel.duration.collectAsState()

    // Mode Selector (Sync Studio vs Karaoke Test vs LRC Text Editor)
    var currentMode by remember { mutableStateOf(LyricsEditorMode.SYNC_STUDIO) }

    // Lyric Editor States
    val linesList = remember { mutableStateListOf<SyncLine>() }
    var rawTextContent by remember { mutableStateOf("") }
    var isDownloadingOnline by remember { mutableStateOf(false) }
    var isAutoSaved by remember { mutableStateOf(true) }

    // History for Undo
    val undoStack = remember { mutableStateListOf<List<SyncLine>>() }

    fun pushUndoState() {
        if (undoStack.size >= 25) {
            undoStack.removeAt(0)
        }
        undoStack.add(linesList.map { it.copy() })
    }

    // Line Edit / Time Shift Dialog States
    var showLineDetailDialog by remember { mutableStateOf<SyncLine?>(null) }
    var expandedLineIndex by remember { mutableStateOf<Int?>(null) }
    var showGlobalShiftDialog by remember { mutableStateOf(false) }
    var customShiftMsInput by remember { mutableStateOf("") }
    var showAddLineDialog by remember { mutableStateOf(false) }
    var newLineText by remember { mutableStateOf("") }

    val lazyListState = rememberLazyListState()
    val colors = SoundboxTheme.colors

    // Immediate Auto-Save to Disk helper
    fun autoSaveCurrentLyrics() {
        val song = currentSong ?: return
        val lrcContent = if (currentMode == LyricsEditorMode.TEXT_EDITOR) {
            rawTextContent
        } else {
            val pairs = linesList.map { Pair(it.timeMs ?: 0L, it.text) }
            LyricsManager.generateLrcContent(pairs)
        }
        LyricsManager.saveLyrics(context, song, lrcContent)
        isAutoSaved = true
    }

    // Function to load song lyrics
    fun reloadSongLyrics(song: Song) {
        val loaded = LyricsManager.loadLyrics(context, song)
        if (!loaded.isNullOrBlank()) {
            rawTextContent = loaded
            val parsed = parseLrcToSyncLines(loaded)
            if (parsed.isNotEmpty()) {
                linesList.clear()
                linesList.addAll(parsed)
            }
        }
    }

    // File Picker for importing .lrc / .txt directly
    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader ->
                    val content = reader.readText()
                    rawTextContent = content
                    val parsed = parseLrcToSyncLines(content)
                    if (parsed.isNotEmpty()) {
                        pushUndoState()
                        linesList.clear()
                        linesList.addAll(parsed)
                        autoSaveCurrentLyrics()
                        Toast.makeText(context, "Imported ${parsed.size} lines from file & saved", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to read file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Load initial lyrics if existing
    LaunchedEffect(currentSong?.id) {
        val song = currentSong ?: return@LaunchedEffect
        reloadSongLyrics(song)
    }

    // Shift all timestamps by delta ms
    fun shiftAllLines(deltaMs: Long, customMessage: String? = null) {
        if (linesList.none { it.timeMs != null }) {
            Toast.makeText(context, "No synced lines found to shift", Toast.LENGTH_SHORT).show()
            return
        }
        pushUndoState()
        val updated = linesList.map { line ->
            if (line.timeMs != null) {
                val newTime = (line.timeMs + deltaMs).coerceAtLeast(0L)
                line.copy(timeMs = newTime)
            } else {
                line
            }
        }
        linesList.clear()
        linesList.addAll(updated)
        autoSaveCurrentLyrics()
        val sign = if (deltaMs >= 0) "+${deltaMs / 1000.0}s" else "${deltaMs / 1000.0}s"
        val msg = customMessage ?: "Shifted all lyrics by $sign (auto-saved)"
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    // Shift a single line timestamp by delta ms
    fun shiftSingleLine(index: Int, deltaMs: Long) {
        if (index in linesList.indices) {
            pushUndoState()
            val current = linesList[index]
            val baseTime = current.timeMs ?: position
            val newTime = (baseTime + deltaMs).coerceAtLeast(0L)
            linesList[index] = current.copy(timeMs = newTime)
            autoSaveCurrentLyrics()
            val sign = if (deltaMs >= 0) "+${deltaMs / 1000.0}s" else "${deltaMs / 1000.0}s"
            Toast.makeText(context, "Line #${index + 1} shifted $sign", Toast.LENGTH_SHORT).show()
        }
    }

    // Tag a line to a specific timestamp
    fun tagLineTimestamp(targetIndex: Int, timeMs: Long) {
        if (targetIndex in linesList.indices) {
            pushUndoState()
            linesList[targetIndex] = linesList[targetIndex].copy(timeMs = timeMs)
            autoSaveCurrentLyrics()
            Toast.makeText(
                context,
                "Line #${targetIndex + 1} set to ${formatLrcTimeLabel(timeMs)}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Smart Video Intro Cutter / Aligner
    // Calculates offset between where user paused audio vs first synced lyric and shifts ALL lines
    fun alignVideoIntroToAudioPosition() {
        if (linesList.isEmpty()) {
            Toast.makeText(context, "No lyrics loaded to align", Toast.LENGTH_SHORT).show()
            return
        }
        val firstSynced = linesList.firstOrNull { it.timeMs != null }
        if (firstSynced == null) {
            // If no lines have timestamps yet, stamp the first line to current audio position
            tagLineTimestamp(0, position)
            Toast.makeText(context, "Set 1st lyric to start @ ${formatLrcTimeLabel(position)}", Toast.LENGTH_SHORT).show()
            return
        }
        val firstOriginalTime = firstSynced.timeMs ?: 0L
        val deltaMs = position - firstOriginalTime
        if (deltaMs == 0L) {
            Toast.makeText(context, "Intro already aligned to current position", Toast.LENGTH_SHORT).show()
            return
        }
        val sign = if (deltaMs >= 0) "+${String.format(Locale.US, "%.2f", deltaMs / 1000.0)}s" else "${String.format(Locale.US, "%.2f", deltaMs / 1000.0)}s"
        shiftAllLines(deltaMs, "✂️ Cut video intro offset ($sign applied to entire song)")
    }

    // Audition preview helper (plays 2.5s from target time then pauses)
    var auditionJob by remember { mutableStateOf<Job?>(null) }
    fun auditionTime(timeMs: Long) {
        auditionJob?.cancel()
        viewModel.seekTo(timeMs)
        if (!isPlaying) {
            viewModel.playPause()
        }
        auditionJob = scope.launch {
            delay(2500)
            if (viewModel.isPlaying.value) {
                viewModel.playPause()
            }
        }
    }

    // Download online lyrics helper
    fun downloadLyricsFromLrclib() {
        val song = currentSong ?: return
        scope.launch {
            isDownloadingOnline = true
            try {
                val fetched = LyricsManager.fetchLyricsOnline(song)
                if (!fetched.isNullOrBlank()) {
                    rawTextContent = fetched
                    val parsed = parseLrcToSyncLines(fetched)
                    if (parsed.isNotEmpty()) {
                        pushUndoState()
                        linesList.clear()
                        linesList.addAll(parsed)
                        autoSaveCurrentLyrics()
                        Toast.makeText(context, "Downloaded & auto-saved synced lyrics!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "No match found on LRCLIB", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isDownloadingOnline = false
            }
        }
    }

    // Manual Save lyrics helper
    fun saveLyricsToDisk() {
        autoSaveCurrentLyrics()
        val song = currentSong ?: return
        Toast.makeText(context, "Lyrics saved successfully for \"${song.title}\"", Toast.LENGTH_SHORT).show()
        scope.launch {
            delay(250)
            onNavigateBack()
        }
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "LYRICS & LRC STUDIO",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.1.sp,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = colors.accentCyan
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = colors.accentLime.copy(alpha = 0.2f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(colors.accentLime)
                                    )
                                    Text(
                                        text = "LIVE SYNC",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        color = colors.accentLime
                                    )
                                }
                            }
                        }
                        if (currentSong != null) {
                            Text(
                                text = "${currentSong!!.title} • ${currentSong!!.artist}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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
                    // Quick Download Online from LRCLIB
                    IconButton(
                        onClick = { downloadLyricsFromLrclib() },
                        enabled = !isDownloadingOnline && currentSong != null
                    ) {
                        if (isDownloadingOnline) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = colors.accentCyan, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.CloudDownload, contentDescription = "Download Lyrics", tint = colors.accentCyan)
                        }
                    }

                    // Import File (.lrc / .txt)
                    IconButton(onClick = { filePickerLauncher.launch("*/*") }) {
                        Icon(Icons.Default.UploadFile, contentDescription = "Import LRC", tint = colors.textPrimary)
                    }

                    // Save Button
                    FilledTonalButton(
                        onClick = { saveLyricsToDisk() },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = colors.accentCyan,
                            contentColor = Color.Black
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Apply", fontWeight = FontWeight.Bold)
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
                            .background(colors.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicOff,
                            contentDescription = "No music",
                            modifier = Modifier.size(36.dp),
                            tint = colors.textSecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No track currently loaded",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Please play a song from your library first to edit or synchronize its lyrics.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
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
                // TRACK HEADER & MINI WAVEFORM DOCK
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.border)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.surfaceVariant)
                            ) {
                                ArtworkThumbnail(
                                    songId = song.id,
                                    title = song.title,
                                    artist = song.artist,
                                    genre = song.genre,
                                    path = song.path,
                                    size = 40f
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${song.artist} • ${formatPositionTime(position)} / ${formatPositionTime(songDuration)}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = colors.accentCyan
                                )
                            }

                            // Synced lines counter badge
                            val syncedCount = linesList.count { it.timeMs != null }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (syncedCount == linesList.size && linesList.isNotEmpty()) colors.accentLime.copy(alpha = 0.2f) else colors.accentCyan.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = if (linesList.isNotEmpty()) "$syncedCount / ${linesList.size} SYNCED" else "EMPTY",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = if (syncedCount == linesList.size && linesList.isNotEmpty()) colors.accentLime else colors.accentCyan,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Interactive Waveform Seekbar
                        PowerampWaveformBar(
                            currentPosition = position,
                            duration = songDuration,
                            isPlaying = isPlaying,
                            onSeek = { targetMs -> viewModel.seekTo(targetMs) },
                            accentColor = colors.accentCyan,
                            seedKey = song.id.toString(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                        )
                    }
                }

                // MODE SELECTOR TABS
                PrimaryTabRow(
                    selectedTabIndex = currentMode.ordinal,
                    containerColor = Color.Transparent,
                    contentColor = colors.accentCyan,
                    modifier = Modifier.padding(horizontal = 14.dp)
                ) {
                    LyricsEditorMode.entries.forEach { mode ->
                        val isSelected = currentMode == mode
                        Tab(
                            selected = isSelected,
                            onClick = {
                                if (mode == LyricsEditorMode.TEXT_EDITOR && currentMode != LyricsEditorMode.TEXT_EDITOR) {
                                    // Synchronize text editor buffer from linesList
                                    val pairs = linesList.map { Pair(it.timeMs ?: 0L, it.text) }
                                    rawTextContent = LyricsManager.generateLrcContent(pairs)
                                } else if (currentMode == LyricsEditorMode.TEXT_EDITOR && mode != LyricsEditorMode.TEXT_EDITOR) {
                                    // Synchronize linesList from rawTextContent
                                    val parsed = parseLrcToSyncLines(rawTextContent)
                                    if (parsed.isNotEmpty()) {
                                        linesList.clear()
                                        linesList.addAll(parsed)
                                    }
                                }
                                currentMode = mode
                            },
                            text = {
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontFamily = FontFamily.Default
                                    ),
                                    color = if (isSelected) colors.accentCyan else colors.textSecondary
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // MAIN CONTENT VIEWPORT BASED ON SELECTED MODE
                when (currentMode) {
                    LyricsEditorMode.SYNC_STUDIO -> {
                        SyncStudioModeView(
                            linesList = linesList,
                            currentPosition = position,
                            isPlaying = isPlaying,
                            songDuration = songDuration,
                            lazyListState = lazyListState,
                            expandedLineIndex = expandedLineIndex,
                            onExpandLine = { expandedLineIndex = if (expandedLineIndex == it) null else it },
                            onSeekTo = { viewModel.seekTo(it) },
                            onPlayPause = { viewModel.playPause() },
                            onShiftAll = { shiftAllLines(it) },
                            onShiftSingle = { idx, delta -> shiftSingleLine(idx, delta) },
                            onTagLine = { targetIndex, timeMs -> tagLineTimestamp(targetIndex, timeMs) },
                            onAlignVideoIntro = { alignVideoIntroToAudioPosition() },
                            onAuditionLine = { timeMs -> auditionTime(timeMs) },
                            onUndo = {
                                if (undoStack.isNotEmpty()) {
                                    val previous = undoStack.removeAt(undoStack.lastIndex)
                                    linesList.clear()
                                    linesList.addAll(previous)
                                    autoSaveCurrentLyrics()
                                    Toast.makeText(context, "Undone last action", Toast.LENGTH_SHORT).show()
                                }
                            },
                            canUndo = undoStack.isNotEmpty(),
                            onEditLine = { line -> showLineDetailDialog = line },
                            onDeleteLine = { idx ->
                                pushUndoState()
                                linesList.removeAt(idx)
                                autoSaveCurrentLyrics()
                            },
                            onAddLine = { showAddLineDialog = true },
                            onShowGlobalShiftDialog = { showGlobalShiftDialog = true },
                            onSwitchToTextMode = {
                                val pairs = linesList.map { Pair(it.timeMs ?: 0L, it.text) }
                                rawTextContent = LyricsManager.generateLrcContent(pairs)
                                currentMode = LyricsEditorMode.TEXT_EDITOR
                            }
                        )
                    }
                    LyricsEditorMode.LIVE_PREVIEW -> {
                        LiveKaraokePreviewModeView(
                            linesList = linesList,
                            currentPosition = position,
                            isPlaying = isPlaying,
                            songDuration = songDuration,
                            onSeekTo = { viewModel.seekTo(it) },
                            onPlayPause = { viewModel.playPause() },
                            onShiftAll = { shiftAllLines(it) },
                            onShiftSingle = { idx, delta -> shiftSingleLine(idx, delta) },
                            onTagLine = { targetIndex, timeMs -> tagLineTimestamp(targetIndex, timeMs) },
                            onClearLineTag = { idx ->
                                pushUndoState()
                                linesList[idx] = linesList[idx].copy(timeMs = null)
                                autoSaveCurrentLyrics()
                                Toast.makeText(context, "Cleared stamp for line #${idx + 1}", Toast.LENGTH_SHORT).show()
                            },
                            onAlignVideoIntro = { alignVideoIntroToAudioPosition() },
                            onAuditionLine = { timeMs -> auditionTime(timeMs) },
                            onUndo = {
                                if (undoStack.isNotEmpty()) {
                                    val previous = undoStack.removeAt(undoStack.lastIndex)
                                    linesList.clear()
                                    linesList.addAll(previous)
                                    autoSaveCurrentLyrics()
                                    Toast.makeText(context, "Undone last action", Toast.LENGTH_SHORT).show()
                                }
                            },
                            canUndo = undoStack.isNotEmpty(),
                            onEditLine = { line -> showLineDetailDialog = line }
                        )
                    }
                    LyricsEditorMode.TEXT_EDITOR -> {
                        FullTextEditorModeView(
                            rawTextContent = rawTextContent,
                            onContentChange = {
                                rawTextContent = it
                                // Also update linesList on change
                                val parsed = parseLrcToSyncLines(it)
                                if (parsed.isNotEmpty()) {
                                    linesList.clear()
                                    linesList.addAll(parsed)
                                }
                                autoSaveCurrentLyrics()
                            },
                            currentPosition = position,
                            onInsertTimestampAtCursor = {
                                val timeTag = "[${formatLrcTimeLabel(position)}]"
                                rawTextContent = if (rawTextContent.isBlank()) timeTag else "$rawTextContent\n$timeTag "
                                val parsed = parseLrcToSyncLines(rawTextContent)
                                if (parsed.isNotEmpty()) {
                                    linesList.clear()
                                    linesList.addAll(parsed)
                                }
                                autoSaveCurrentLyrics()
                            },
                            onShiftAll = { deltaMs ->
                                val parsed = parseLrcToSyncLines(rawTextContent)
                                val updated = parsed.map { line ->
                                    val newT = if (line.timeMs != null) (line.timeMs + deltaMs).coerceAtLeast(0L) else null
                                    line.copy(timeMs = newT)
                                }
                                val pairs = updated.map { Pair(it.timeMs ?: 0L, it.text) }
                                rawTextContent = LyricsManager.generateLrcContent(pairs)
                                linesList.clear()
                                linesList.addAll(updated)
                                autoSaveCurrentLyrics()
                                val sign = if (deltaMs >= 0) "+${deltaMs / 1000.0}s" else "${deltaMs / 1000.0}s"
                                Toast.makeText(context, "Shifted text timestamps by $sign", Toast.LENGTH_SHORT).show()
                            },
                            onStripTimestamps = {
                                val lines = rawTextContent.lines()
                                val clean = lines.map { it.replace(Regex("\\[\\d{2}:\\d{2}(?:\\.\\d{1,3})?]"), "").trim() }
                                    .filter { it.isNotBlank() }
                                    .joinToString("\n")
                                rawTextContent = clean
                                linesList.clear()
                                linesList.addAll(clean.lines().mapIndexed { i, t -> SyncLine(i, t, null) })
                                autoSaveCurrentLyrics()
                                Toast.makeText(context, "Removed all timestamp tags", Toast.LENGTH_SHORT).show()
                            },
                            onCopyAll = {
                                clipboardManager.setText(AnnotatedString(rawTextContent))
                                Toast.makeText(context, "Copied lyrics to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            onPasteClipboard = {
                                val clip = clipboardManager.getText()?.text
                                if (!clip.isNullOrBlank()) {
                                    rawTextContent = clip
                                    val parsed = parseLrcToSyncLines(clip)
                                    if (parsed.isNotEmpty()) {
                                        linesList.clear()
                                        linesList.addAll(parsed)
                                    }
                                    autoSaveCurrentLyrics()
                                    Toast.makeText(context, "Pasted & auto-saved from clipboard", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // LINE EDIT / TIMING DIALOG
    if (showLineDetailDialog != null) {
        val target = showLineDetailDialog!!
        var editLineText by remember { mutableStateOf(target.text) }
        var timeMinutesEdit by remember {
            val ms = target.timeMs ?: 0L
            mutableStateOf(String.format(Locale.US, "%02d", ms / 60000))
        }
        var timeSecondsEdit by remember {
            val ms = target.timeMs ?: 0L
            mutableStateOf(String.format(Locale.US, "%02d", (ms % 60000) / 1000))
        }
        var timeHundredthsEdit by remember {
            val ms = target.timeMs ?: 0L
            mutableStateOf(String.format(Locale.US, "%02d", (ms % 1000) / 10))
        }

        AlertDialog(
            containerColor = colors.dialogBackground,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
            onDismissRequest = { showLineDetailDialog = null },
            title = {
                Text(
                    "Edit Line #${target.index + 1}",
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = colors.accentCyan
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = editLineText,
                        onValueChange = { editLineText = it },
                        label = { Text("Lyric Line Text", color = colors.textSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = colors.surface,
                            unfocusedContainerColor = colors.surface,
                            focusedBorderColor = colors.accentCyan,
                            unfocusedBorderColor = colors.border,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        "Manual Timestamp (mm : ss . xx):",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = colors.accentCyan
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = timeMinutesEdit,
                            onValueChange = { if (it.length <= 2 && it.all { c -> c.isDigit() }) timeMinutesEdit = it },
                            label = { Text("Min") },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = colors.surface,
                                unfocusedContainerColor = colors.surface,
                                focusedBorderColor = colors.accentCyan,
                                unfocusedBorderColor = colors.border,
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace)
                        )
                        Text(":", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
                        OutlinedTextField(
                            value = timeSecondsEdit,
                            onValueChange = { if (it.length <= 2 && it.all { c -> c.isDigit() }) timeSecondsEdit = it },
                            label = { Text("Sec") },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = colors.surface,
                                unfocusedContainerColor = colors.surface,
                                focusedBorderColor = colors.accentCyan,
                                unfocusedBorderColor = colors.border,
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace)
                        )
                        Text(".", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
                        OutlinedTextField(
                            value = timeHundredthsEdit,
                            onValueChange = { if (it.length <= 2 && it.all { c -> c.isDigit() }) timeHundredthsEdit = it },
                            label = { Text("Hund") },
                            modifier = Modifier.weight(1.2f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = colors.surface,
                                unfocusedContainerColor = colors.surface,
                                focusedBorderColor = colors.accentCyan,
                                unfocusedBorderColor = colors.border,
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace)
                        )
                    }

                    // Quick Timestamp Helper Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val min = position / 60000
                                val sec = (position % 60000) / 1000
                                val hund = (position % 1000) / 10
                                timeMinutesEdit = String.format(Locale.US, "%02d", min)
                                timeSecondsEdit = String.format(Locale.US, "%02d", sec)
                                timeHundredthsEdit = String.format(Locale.US, "%02d", hund)
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            Text("Set @ Audio (${formatPositionTime(position)})", style = MaterialTheme.typography.labelSmall)
                        }

                        if (target.timeMs != null) {
                            OutlinedButton(
                                onClick = {
                                    timeMinutesEdit = ""
                                    timeSecondsEdit = ""
                                    timeHundredthsEdit = ""
                                },
                                modifier = Modifier.weight(0.6f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252))
                            ) {
                                Text("Clear", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pushUndoState()
                        val m = timeMinutesEdit.toLongOrNull()
                        val s = timeSecondsEdit.toLongOrNull()
                        val h = timeHundredthsEdit.toLongOrNull() ?: 0L
                        val newTimeMs = if (m != null && s != null) {
                            (m * 60000) + (s * 1000) + (h * 10)
                        } else null

                        val idx = target.index
                        if (idx in linesList.indices) {
                            linesList[idx] = linesList[idx].copy(
                                text = editLineText.trim(),
                                timeMs = newTimeMs
                            )
                            autoSaveCurrentLyrics()
                        }
                        showLineDetailDialog = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.accentCyan)
                ) {
                    Text("Apply & Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLineDetailDialog = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.textSecondary)
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // GLOBAL TIMING SHIFT DIALOG
    if (showGlobalShiftDialog) {
        AlertDialog(
            containerColor = colors.dialogBackground,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
            onDismissRequest = { showGlobalShiftDialog = false },
            title = {
                Text(
                    "Global Timing Shift",
                    fontWeight = FontWeight.Bold,
                    color = colors.accentCyan
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Shift all synchronized lines forward (delay) or backward (advance).",
                        style = MaterialTheme.typography.bodySmall
                    )

                    OutlinedTextField(
                        value = customShiftMsInput,
                        onValueChange = { customShiftMsInput = it },
                        placeholder = { Text("e.g. 500 for +0.5s or -500 for -0.5s") },
                        label = { Text("Offset in milliseconds (ms)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = colors.surface,
                            unfocusedContainerColor = colors.surface,
                            focusedBorderColor = colors.accentCyan,
                            unfocusedBorderColor = colors.border,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Presets:", style = MaterialTheme.typography.labelSmall, color = colors.accentCyan)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("-5000", "-1000", "-500", "+500", "+1000", "+5000").forEach { preset ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = colors.surfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { customShiftMsInput = preset }
                            ) {
                                Text(
                                    text = preset,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, textAlign = TextAlign.Center),
                                    color = colors.textPrimary,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val parsed = customShiftMsInput.replace("+", "").toLongOrNull()
                        if (parsed != null && parsed != 0L) {
                            shiftAllLines(parsed)
                        }
                        showGlobalShiftDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.accentCyan)
                ) {
                    Text("Apply Shift", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showGlobalShiftDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.textSecondary)
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // ADD NEW LINE DIALOG
    if (showAddLineDialog) {
        AlertDialog(
            containerColor = colors.dialogBackground,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
            onDismissRequest = { showAddLineDialog = false },
            title = {
                Text("Insert Lyric Line", fontWeight = FontWeight.Bold, color = colors.accentCyan)
            },
            text = {
                OutlinedTextField(
                    value = newLineText,
                    onValueChange = { newLineText = it },
                    label = { Text("Line Text") },
                    placeholder = { Text("Enter line text...") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = colors.surface,
                        unfocusedContainerColor = colors.surface,
                        focusedBorderColor = colors.accentCyan,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newLineText.isNotBlank()) {
                            pushUndoState()
                            linesList.add(SyncLine(linesList.size, newLineText.trim(), null))
                            autoSaveCurrentLyrics()
                            newLineText = ""
                        }
                        showAddLineDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.accentCyan)
                ) {
                    Text("Add Line", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAddLineDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.textSecondary)
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * 1. SYNC STUDIO VIEW (Interactive List with Video Intro Cutter, Nudge Controls, and Vocal Stamping)
 */
@Composable
fun SyncStudioModeView(
    linesList: List<SyncLine>,
    currentPosition: Long,
    isPlaying: Boolean,
    songDuration: Long,
    lazyListState: androidx.compose.foundation.lazy.LazyListState,
    expandedLineIndex: Int?,
    onExpandLine: (Int) -> Unit,
    onSeekTo: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onShiftAll: (Long) -> Unit,
    onShiftSingle: (Int, Long) -> Unit,
    onTagLine: (Int, Long) -> Unit,
    onAlignVideoIntro: () -> Unit,
    onAuditionLine: (Long) -> Unit,
    onUndo: () -> Unit,
    canUndo: Boolean,
    onEditLine: (SyncLine) -> Unit,
    onDeleteLine: (Int) -> Unit,
    onAddLine: () -> Unit,
    onShowGlobalShiftDialog: () -> Unit,
    onSwitchToTextMode: () -> Unit
) {
    val colors = SoundboxTheme.colors
    val nextSyncIndex = linesList.indexOfFirst { it.timeMs == null }

    // Auto-scroll to next line waiting to be tagged
    LaunchedEffect(nextSyncIndex) {
        if (nextSyncIndex >= 0 && linesList.isNotEmpty()) {
            val target = (nextSyncIndex - 2).coerceAtLeast(0)
            lazyListState.animateScrollToItem(target)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (linesList.isEmpty()) {
            // Empty state helper
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.FormatQuote, contentDescription = null, tint = colors.accentCyan, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No Lyrics Text Loaded", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Paste or type lyrics text line-by-line in the text editor, or import an LRC file from top toolbar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onSwitchToTextMode,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accentCyan, contentColor = Color.Black)
                    ) {
                        Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Text Editor & Paste", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            // SMART VIDEO INTRO CUTTER & TIMING CALIBRATION BAR
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                color = colors.surfaceVariant.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, colors.border)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.ContentCut, contentDescription = null, tint = colors.accentLime, modifier = Modifier.size(14.dp))
                            Text(
                                text = "VIDEO INTRO CUTTER & CALIBRATION",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 9.sp,
                                    letterSpacing = 1.sp,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = colors.accentLime
                            )
                        }

                        Text(
                            text = "Custom Shift",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = colors.accentCyan,
                            modifier = Modifier.clickable { onShowGlobalShiftDialog() }
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // 1-Tap Align Intro to Audio Position Button
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colors.accentLime.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, colors.accentLime.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAlignVideoIntro() }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.MovieFilter, contentDescription = null, tint = colors.accentLime, modifier = Modifier.size(16.dp))
                                Column {
                                    Text(
                                        text = "Cut Video Intro & Align Vocals to Now",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                                        color = colors.accentLime
                                    )
                                    Text(
                                        text = "Pausing at vocal start? Shifts entire song to @ ${formatLrcTimeLabel(currentPosition)}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                                        color = colors.textSecondary
                                    )
                                }
                            }
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = colors.accentLime, modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Direct +/- Buttons Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val shiftOptions = listOf(
                            "-5.0s" to -5000L,
                            "-1.0s" to -1000L,
                            "-0.5s" to -500L,
                            "+0.5s" to 500L,
                            "+1.0s" to 1000L,
                            "+5.0s" to 5000L
                        )

                        shiftOptions.forEach { (label, delta) ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (delta < 0) colors.accentAmber.copy(alpha = 0.15f) else colors.accentCyan.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, if (delta < 0) colors.accentAmber.copy(alpha = 0.4f) else colors.accentCyan.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onShiftAll(delta) }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(vertical = 5.dp)
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        color = if (delta < 0) colors.accentAmber else colors.accentCyan
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // SCROLLABLE LIST OF LYRICS WITH INLINE MICRO-ADJUST CONTROLS
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp)
            ) {
                itemsIndexed(linesList) { index, line ->
                    val isTarget = index == nextSyncIndex
                    val isExpanded = expandedLineIndex == index
                    val isPassed = line.timeMs != null && currentPosition >= line.timeMs

                    Surface(
                        color = when {
                            isTarget -> colors.accentCyan.copy(alpha = 0.15f)
                            isPassed -> colors.surfaceVariant
                            else -> colors.surface
                        },
                        border = BorderStroke(
                            1.dp,
                            if (isTarget) colors.accentCyan else if (isExpanded) colors.accentLime else colors.border
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onExpandLine(index) }
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Timestamp badge / Seek trigger
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (line.timeMs != null) colors.accentCyan.copy(alpha = 0.2f) else colors.surfaceVariant,
                                    modifier = Modifier.clickable {
                                        if (line.timeMs != null) {
                                            onSeekTo(line.timeMs)
                                        } else {
                                            onTagLine(index, currentPosition)
                                        }
                                    }
                                ) {
                                    Text(
                                        text = if (line.timeMs != null) formatLrcTimeLabel(line.timeMs) else "--:--.--",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp
                                        ),
                                        color = if (line.timeMs != null) colors.accentCyan else colors.textSecondary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                    text = line.text,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isTarget || isPassed) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (isTarget) colors.accentCyan else if (isPassed) colors.textPrimary else colors.textSecondary,
                                    modifier = Modifier.weight(1f)
                                )

                                if (isTarget) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = colors.accentLime
                                    ) {
                                        Text(
                                            "NEXT",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Black,
                                                fontSize = 8.sp,
                                                fontFamily = FontFamily.Monospace
                                            ),
                                            color = Color.Black,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }

                                // Quick 1-tap Audition / Tag Buttons
                                if (line.timeMs != null) {
                                    IconButton(
                                        onClick = { onAuditionLine(line.timeMs) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayCircleOutline,
                                            contentDescription = "Audition line",
                                            tint = colors.accentLime,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                } else {
                                    IconButton(
                                        onClick = { onTagLine(index, currentPosition) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.BookmarkAdd,
                                            contentDescription = "Stamp current time",
                                            tint = colors.accentCyan,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { onExpandLine(index) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.MoreHoriz,
                                        contentDescription = "Options",
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // EXPANDABLE INDIVIDUAL LINE MICRO-ADJUSTMENT DRAWER
                            AnimatedVisibility(
                                visible = isExpanded,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                ) {
                                    HorizontalDivider(color = colors.border.copy(alpha = 0.5f), modifier = Modifier.padding(bottom = 6.dp))

                                    Text(
                                        text = "MICRO-ADJUST LINE #${index + 1} TIMING:",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        color = colors.accentCyan
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))

                                    // Line-specific +/- buttons
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        listOf("-1s" to -1000L, "-0.1s" to -100L, "+0.1s" to 100L, "+1s" to 1000L).forEach { (label, delta) ->
                                            OutlinedButton(
                                                onClick = { onShiftSingle(index, delta) },
                                                modifier = Modifier.weight(1f),
                                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                                            ) {
                                                Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    // Line actions (Set @ Playhead, Edit text, Audition, Delete)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Button(
                                            onClick = { onTagLine(index, currentPosition) },
                                            modifier = Modifier.weight(1.2f),
                                            colors = ButtonDefaults.buttonColors(containerColor = colors.accentCyan, contentColor = Color.Black),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Set @ Now", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                        }

                                        if (line.timeMs != null) {
                                            OutlinedButton(
                                                onClick = { onAuditionLine(line.timeMs) },
                                                modifier = Modifier.weight(1f),
                                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                                            ) {
                                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.accentLime)
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text("Audition", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }

                                        OutlinedButton(
                                            onClick = { onEditLine(line) },
                                            modifier = Modifier.weight(0.9f),
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text("Edit", style = MaterialTheme.typography.labelSmall)
                                        }

                                        OutlinedButton(
                                            onClick = { onDeleteLine(index) },
                                            modifier = Modifier.weight(0.7f),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    // Add Line button at the bottom of the list
                    OutlinedButton(
                        onClick = onAddLine,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.accentCyan)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add New Line", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // BOTTOM SYNCHRONIZER PLAYBACK & BIG TAG ACTION DOCK
            Surface(
                color = colors.surface,
                border = BorderStroke(1.dp, colors.border),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Transport controls Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Replay 5s
                        IconButton(onClick = { onSeekTo((currentPosition - 5000).coerceAtLeast(0)) }) {
                            Icon(Icons.Default.Replay5, contentDescription = "Back 5s", tint = colors.accentCyan, modifier = Modifier.size(24.dp))
                        }

                        // Replay 1s
                        IconButton(onClick = { onSeekTo((currentPosition - 1000).coerceAtLeast(0)) }) {
                            Icon(Icons.Default.Replay, contentDescription = "Back 1s", tint = colors.accentCyan, modifier = Modifier.size(20.dp))
                        }

                        // Play / Pause Circle
                        Surface(
                            onClick = onPlayPause,
                            shape = CircleShape,
                            color = colors.accentCyan,
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = Color.Black,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // Forward 1s
                        IconButton(onClick = { onSeekTo((currentPosition + 1000).coerceAtMost(songDuration)) }) {
                            Icon(Icons.Default.Forward10, contentDescription = "Forward 1s", tint = colors.accentCyan, modifier = Modifier.size(20.dp))
                        }

                        // Forward 5s
                        IconButton(onClick = { onSeekTo((currentPosition + 5000).coerceAtMost(songDuration)) }) {
                            Icon(Icons.Default.Forward5, contentDescription = "Forward 5s", tint = colors.accentCyan, modifier = Modifier.size(24.dp))
                        }

                        // Undo Button
                        IconButton(
                            onClick = onUndo,
                            enabled = canUndo
                        ) {
                            Icon(
                                Icons.Default.Undo,
                                contentDescription = "Undo",
                                tint = if (canUndo) colors.accentAmber else colors.textMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // PRIMARY ACTION MEGA BUTTON (Prominent Tag Button)
                    Button(
                        onClick = {
                            if (nextSyncIndex >= 0) {
                                onTagLine(nextSyncIndex, currentPosition)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (nextSyncIndex >= 0) colors.accentCyan else colors.accentLime,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (nextSyncIndex >= 0) {
                                "STAMP NEXT LINE @ ${formatLrcTimeLabel(currentPosition)}"
                            } else {
                                "ALL ${linesList.size} LINES SYNCED & SAVED"
                            },
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.8.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * 2. LIVE KARAOKE TEST PREVIEW MODE
 */
@Composable
fun LiveKaraokePreviewModeView(
    linesList: List<SyncLine>,
    currentPosition: Long,
    isPlaying: Boolean,
    songDuration: Long,
    onSeekTo: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onShiftAll: (Long) -> Unit,
    onShiftSingle: (Int, Long) -> Unit,
    onTagLine: (Int, Long) -> Unit,
    onClearLineTag: (Int) -> Unit,
    onAlignVideoIntro: () -> Unit,
    onAuditionLine: (Long) -> Unit,
    onUndo: () -> Unit,
    canUndo: Boolean,
    onEditLine: (SyncLine) -> Unit
) {
    val colors = SoundboxTheme.colors
    val allLinesWithOriginalIndex = remember(linesList) {
        linesList.mapIndexed { index, syncLine -> Pair(index, syncLine) }
    }
    val syncedLines = remember(linesList) { linesList.filter { it.timeMs != null }.sortedBy { it.timeMs } }
    val activeIndex = remember(syncedLines, currentPosition) {
        syncedLines.indexOfLast { currentPosition >= it.timeMs!! }
    }
    val previewListState = rememberLazyListState()

    // Next target line for the top stamp deck (first unsynced, or the line right after currently active)
    var selectedTargetLineIdx by remember { mutableStateOf<Int?>(null) }
    val effectiveTargetIdx = remember(selectedTargetLineIdx, linesList, activeIndex) {
        selectedTargetLineIdx?.takeIf { it in linesList.indices }
            ?: if (activeIndex >= 0 && activeIndex < syncedLines.size) {
                val activeLine = syncedLines[activeIndex]
                val currentIdx = linesList.indexOfFirst { it.timeMs == activeLine.timeMs && it.text == activeLine.text }
                if (currentIdx in 0 until linesList.size - 1) currentIdx + 1 else currentIdx
            } else {
                val firstUnsynced = linesList.indexOfFirst { it.timeMs == null }
                if (firstUnsynced >= 0) firstUnsynced else 0
            }
    }

    var autoScrollEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(activeIndex, isPlaying) {
        if (autoScrollEnabled && isPlaying && activeIndex in syncedLines.indices) {
            val originalIndex = linesList.indexOfFirst { it.timeMs == syncedLines[activeIndex].timeMs && it.text == syncedLines[activeIndex].text }
            val scrollTarget = if (originalIndex >= 0) (originalIndex - 2).coerceAtLeast(0) else (activeIndex - 2).coerceAtLeast(0)
            previewListState.animateScrollToItem(scrollTarget)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (linesList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No lyrics available to preview. Add or import lyrics first.", color = colors.textSecondary)
            }
        } else {
            // VOCAL STAMPING & INTRO CUTTER MASTER DECK
            Surface(
                color = colors.surfaceVariant.copy(alpha = 0.85f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, colors.border)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    // Status Row: Audio Position & Play/Pause indicator
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isPlaying) colors.accentLime.copy(alpha = 0.2f) else colors.accentAmber.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, if (isPlaying) colors.accentLime else colors.accentAmber)
                            ) {
                                Text(
                                    text = if (isPlaying) "▶ PLAYING" else "⏸ PAUSED (Vocal Start)",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = if (isPlaying) colors.accentLime else colors.accentAmber,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }

                            Text(
                                text = formatLrcTimeLabel(currentPosition),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = colors.accentCyan
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (canUndo) {
                                IconButton(onClick = onUndo, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = colors.accentCyan, modifier = Modifier.size(18.dp))
                                }
                            }
                            IconButton(
                                onClick = { autoScrollEnabled = !autoScrollEnabled },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = if (autoScrollEnabled) Icons.Default.Sync else Icons.Default.SyncDisabled,
                                    contentDescription = "Toggle Auto-scroll",
                                    tint = if (autoScrollEnabled) colors.accentCyan else colors.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Target line indicator
                    val targetLine = linesList.getOrNull(effectiveTargetIdx)
                    if (targetLine != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colors.surface,
                            border = BorderStroke(1.dp, colors.accentCyan.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = colors.accentCyan,
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "${effectiveTargetIdx + 1}",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 9.sp,
                                                    color = Color.Black
                                                )
                                            )
                                        }
                                    }
                                    Text(
                                        text = targetLine.text.ifBlank { "🎵 Instrumental" },
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (targetLine.timeMs != null) {
                                    Text(
                                        text = "@ ${formatLrcTimeLabel(targetLine.timeMs)}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 9.sp),
                                        color = colors.accentLime
                                    )
                                } else {
                                    Text(
                                        text = "[Not synced]",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = colors.accentAmber
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // 2 Core Action Buttons: 📍 STAMP VOCAL HERE and ✂️ CUT VIDEO INTRO
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Stamp Vocal Button
                        Button(
                            onClick = {
                                onTagLine(effectiveTargetIdx, currentPosition)
                                // Advance selection to next line
                                if (effectiveTargetIdx < linesList.size - 1) {
                                    selectedTargetLineIdx = effectiveTargetIdx + 1
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accentCyan, contentColor = Color.Black),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1.1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Stamp Vocal @ ${formatPositionTime(currentPosition)}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, fontSize = 11.sp, color = Color.Black)
                            )
                        }

                        // 1-Tap Cut Video Intro Button
                        OutlinedButton(
                            onClick = onAlignVideoIntro,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(0.9f),
                            border = BorderStroke(1.dp, colors.accentLime),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.accentLime)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Cut Video Intro",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp, color = colors.accentLime)
                            )
                        }
                    }
                }
            }

            // Live Karaoke scrolling list
            LazyColumn(
                state = previewListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp)
            ) {
                itemsIndexed(allLinesWithOriginalIndex) { _, pair ->
                    val originalIdx = pair.first
                    val line = pair.second
                    val isLineActive = line.timeMs != null && activeIndex >= 0 &&
                            syncedLines.getOrNull(activeIndex)?.timeMs == line.timeMs &&
                            syncedLines.getOrNull(activeIndex)?.text == line.text
                    val isTarget = originalIdx == effectiveTargetIdx

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = when {
                            isLineActive -> colors.accentCyan.copy(alpha = 0.22f)
                            isTarget -> colors.accentCyan.copy(alpha = 0.08f)
                            line.timeMs == null -> colors.surfaceVariant.copy(alpha = 0.35f)
                            else -> colors.surface
                        },
                        border = when {
                            isLineActive -> BorderStroke(2.dp, colors.accentCyan)
                            isTarget -> BorderStroke(1.5.dp, colors.accentCyan.copy(alpha = 0.7f))
                            else -> BorderStroke(1.dp, colors.border.copy(alpha = 0.5f))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedTargetLineIdx = originalIdx
                                if (line.timeMs != null) {
                                    onSeekTo(line.timeMs)
                                }
                            }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            // Top Row: Line Number, Lyric Text, and Target Badge
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = when {
                                        isLineActive -> colors.accentCyan
                                        isTarget -> colors.accentLime
                                        else -> colors.surfaceVariant
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${originalIdx + 1}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Black,
                                                fontFamily = FontFamily.Monospace
                                            ),
                                            color = if (isLineActive || isTarget) Color.Black else colors.textSecondary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Text(
                                    text = line.text.ifBlank { "🎵 Instrumental" },
                                    style = if (isLineActive)
                                        MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, fontSize = 16.sp)
                                    else
                                        MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                    color = when {
                                        isLineActive -> colors.accentCyan
                                        line.timeMs == null -> colors.textSecondary.copy(alpha = 0.6f)
                                        else -> colors.textPrimary
                                    },
                                    modifier = Modifier.weight(1f)
                                )

                                if (isTarget) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = colors.accentCyan.copy(alpha = 0.2f),
                                        border = BorderStroke(0.5.dp, colors.accentCyan)
                                    ) {
                                        Text(
                                            text = "TARGET",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Black,
                                                fontSize = 8.sp,
                                                fontFamily = FontFamily.Monospace
                                            ),
                                            color = colors.accentCyan,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Bottom Interactive Action Bar for this line
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Timestamp badge
                                if (line.timeMs != null) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isLineActive) colors.accentLime.copy(alpha = 0.2f) else colors.surfaceVariant,
                                        modifier = Modifier.clickable { onSeekTo(line.timeMs) }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = if (isLineActive) colors.accentLime else colors.accentCyan, modifier = Modifier.size(12.dp))
                                            Text(
                                                text = formatLrcTimeLabel(line.timeMs),
                                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                                color = if (isLineActive) colors.accentLime else colors.accentCyan
                                            )
                                        }
                                    }
                                } else {
                                    Text(
                                        text = "[Tap Stamp to sync]",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                                        color = colors.accentAmber
                                    )
                                }

                                // Quick Actions for this line
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // 1-Tap Stamp to current audio position
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = colors.accentCyan.copy(alpha = 0.18f),
                                        border = BorderStroke(1.dp, colors.accentCyan.copy(alpha = 0.5f)),
                                        modifier = Modifier.clickable {
                                            selectedTargetLineIdx = originalIdx
                                            onTagLine(originalIdx, currentPosition)
                                            if (originalIdx < linesList.size - 1) {
                                                selectedTargetLineIdx = originalIdx + 1
                                            }
                                        }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        ) {
                                            Icon(Icons.Default.BookmarkBorder, contentDescription = null, tint = colors.accentCyan, modifier = Modifier.size(12.dp))
                                            Text(
                                                text = "Stamp @ ${formatPositionTime(currentPosition)}",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
                                                color = colors.accentCyan
                                            )
                                        }
                                    }

                                    if (line.timeMs != null) {
                                        // Audition 3s
                                        IconButton(
                                            onClick = { onAuditionLine(line.timeMs) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayCircle,
                                                contentDescription = "Audition",
                                                tint = colors.accentLime,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        // Micro Nudges: -0.1s, +0.1s
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = colors.surfaceVariant,
                                            modifier = Modifier.clickable { onShiftSingle(originalIdx, -100L) }
                                        ) {
                                            Text(
                                                text = "-0.1s",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                                color = colors.textSecondary,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = colors.surfaceVariant,
                                            modifier = Modifier.clickable { onShiftSingle(originalIdx, 100L) }
                                        ) {
                                            Text(
                                                text = "+0.1s",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                                color = colors.textSecondary,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }

                                        // Clear Tag button
                                        IconButton(
                                            onClick = { onClearLineTag(originalIdx) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Clear Tag",
                                                tint = colors.textSecondary.copy(alpha = 0.6f),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }

                                    // Edit Text
                                    IconButton(
                                        onClick = { onEditLine(line) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit Text",
                                            tint = colors.textSecondary.copy(alpha = 0.7f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Transport & Global Fine-Tuning Dock
            Surface(
                color = colors.surface,
                border = BorderStroke(1.dp, colors.border),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Global Calibration Nudge Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("-1.0s" to -1000L, "-0.5s" to -500L, "-0.1s" to -100L, "+0.1s" to 100L, "+0.5s" to 500L, "+1.0s" to 1000L).forEach { (label, delta) ->
                            OutlinedButton(
                                onClick = { onShiftAll(delta) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                            ) {
                                Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Transport controls Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { onSeekTo((currentPosition - 5000).coerceAtLeast(0)) }) {
                            Icon(Icons.Default.Replay5, contentDescription = "Back 5s", tint = colors.accentCyan, modifier = Modifier.size(22.dp))
                        }
                        IconButton(onClick = { onSeekTo((currentPosition - 1000).coerceAtLeast(0)) }) {
                            Icon(Icons.Default.Replay, contentDescription = "Back 1s", tint = colors.accentCyan, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = onPlayPause) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                contentDescription = "Play/Pause",
                                tint = colors.accentCyan,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        IconButton(onClick = { onSeekTo((currentPosition + 1000).coerceAtMost(songDuration)) }) {
                            Icon(Icons.Default.Forward10, contentDescription = "Forward 1s", tint = colors.accentCyan, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { onSeekTo((currentPosition + 5000).coerceAtMost(songDuration)) }) {
                            Icon(Icons.Default.Forward5, contentDescription = "Forward 5s", tint = colors.accentCyan, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * 3. FULL-SCREEN TEXT / LRC CODE EDITOR VIEW
 */
@Composable
fun FullTextEditorModeView(
    rawTextContent: String,
    onContentChange: (String) -> Unit,
    currentPosition: Long,
    onInsertTimestampAtCursor: () -> Unit,
    onShiftAll: (Long) -> Unit,
    onStripTimestamps: () -> Unit,
    onCopyAll: () -> Unit,
    onPasteClipboard: () -> Unit
) {
    val colors = SoundboxTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 4.dp)
    ) {
        // Quick Action Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onInsertTimestampAtCursor,
                colors = ButtonDefaults.buttonColors(containerColor = colors.accentCyan, contentColor = Color.Black),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Insert [${formatLrcTimeLabel(currentPosition)}]", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
            }

            OutlinedButton(
                onClick = onPasteClipboard,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Paste", style = MaterialTheme.typography.labelSmall)
            }

            OutlinedButton(
                onClick = onCopyAll,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Copy All", style = MaterialTheme.typography.labelSmall)
            }

            OutlinedButton(
                onClick = { onShiftAll(500L) },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Text("+0.5s", style = MaterialTheme.typography.labelSmall)
            }

            OutlinedButton(
                onClick = { onShiftAll(-500L) },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Text("-0.5s", style = MaterialTheme.typography.labelSmall)
            }

            OutlinedButton(
                onClick = onStripTimestamps,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.ClearAll, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(2.dp))
                Text("Strip Tags", style = MaterialTheme.typography.labelSmall)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Main raw text area
        OutlinedTextField(
            value = rawTextContent,
            onValueChange = onContentChange,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            placeholder = {
                Text(
                    "Paste or type lyrics here...\nFormat:\n[00:12.50] First line of song\n[00:16.80] Second line of song",
                    color = colors.textSecondary.copy(alpha = 0.5f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                focusedBorderColor = colors.accentCyan,
                unfocusedBorderColor = colors.border,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary
            ),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
        )
    }
}

private fun parseLrcToSyncLines(lrcText: String): List<SyncLine> {
    val result = mutableListOf<SyncLine>()
    val lines = lrcText.lines()
    var indexCounter = 0

    for (line in lines) {
        if (line.isBlank()) continue
        val match = "\\[(\\d{2}):(\\d{2})(?:[.:](\\d{2,3}))?](.*)".toRegex().matchEntire(line.trim())
        if (match != null) {
            val min = match.groupValues[1].toLongOrNull() ?: 0L
            val sec = match.groupValues[2].toLongOrNull() ?: 0L
            val fracStr = match.groupValues[3]
            val fracMs = when (fracStr.length) {
                1 -> fracStr.toLong() * 100
                2 -> fracStr.toLong() * 10
                3 -> fracStr.toLong()
                else -> 0L
            }
            val totalMs = (min * 60000) + (sec * 1000) + fracMs
            val textVal = match.groupValues[4].trim()
            result.add(SyncLine(indexCounter++, textVal, totalMs))
        } else {
            if (!line.trim().startsWith("[ar:") && !line.trim().startsWith("[ti:") && !line.trim().startsWith("[al:") && !line.trim().startsWith("[by:")) {
                result.add(SyncLine(indexCounter++, line.trim(), null))
            }
        }
    }
    return result
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
