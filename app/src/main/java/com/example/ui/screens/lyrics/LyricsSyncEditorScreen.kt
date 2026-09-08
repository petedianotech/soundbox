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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

data class SyncLine(
    val index: Int,
    val text: String,
    val timeMs: Long? = null
)

enum class LyricsEditorMode(val label: String, val iconName: String) {
    SYNC_STUDIO("Sync & Calibrate", "Tune"),
    TEXT_EDITOR("LRC & Text Editor", "EditNote"),
    LIVE_PREVIEW("Karaoke Test", "Mic")
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

    // Mode Selector (Sync Studio vs Full-Screen Text Editor vs Karaoke Preview)
    var currentMode by remember { mutableStateOf(LyricsEditorMode.SYNC_STUDIO) }

    // Lyric Editor States
    val linesList = remember { mutableStateListOf<SyncLine>() }
    var rawTextContent by remember { mutableStateOf("") }
    var isDownloadingOnline by remember { mutableStateOf(false) }

    // History for Undo
    val undoStack = remember { mutableStateListOf<List<SyncLine>>() }

    fun pushUndoState() {
        if (undoStack.size >= 15) {
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
                        Toast.makeText(context, "Imported ${parsed.size} lines from file", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to read file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
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

    // Load initial lyrics if existing
    LaunchedEffect(currentSong?.id) {
        val song = currentSong ?: return@LaunchedEffect
        reloadSongLyrics(song)
    }

    // Shift all timestamps by delta ms
    fun shiftAllLines(deltaMs: Long) {
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
        val sign = if (deltaMs >= 0) "+${deltaMs / 1000.0}s" else "${deltaMs / 1000.0}s"
        Toast.makeText(context, "Shifted all lyrics by $sign", Toast.LENGTH_SHORT).show()
    }

    // Shift a single line timestamp by delta ms
    fun shiftSingleLine(index: Int, deltaMs: Long) {
        if (index in linesList.indices) {
            pushUndoState()
            val current = linesList[index]
            val baseTime = current.timeMs ?: position
            val newTime = (baseTime + deltaMs).coerceAtLeast(0L)
            linesList[index] = current.copy(timeMs = newTime)
            val sign = if (deltaMs >= 0) "+${deltaMs / 1000.0}s" else "${deltaMs / 1000.0}s"
            Toast.makeText(context, "Line #${index + 1} shifted $sign", Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(context, "Downloaded & loaded synced lyrics!", Toast.LENGTH_SHORT).show()
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

    // Save lyrics helper
    fun saveLyricsToDisk() {
        val song = currentSong ?: return
        val lrcContent = if (currentMode == LyricsEditorMode.TEXT_EDITOR) {
            rawTextContent
        } else {
            val pairs = linesList.map { Pair(it.timeMs ?: 0L, it.text) }
            LyricsManager.generateLrcContent(pairs)
        }

        LyricsManager.saveLyrics(context, song, lrcContent)
        Toast.makeText(context, "Lyrics saved successfully for \"${song.title}\"", Toast.LENGTH_SHORT).show()
        scope.launch {
            delay(300)
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
                                    letterSpacing = 1.2.sp,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = colors.accentCyan
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = colors.accentLime.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "PRO EDITOR",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = colors.accentLime,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
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
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save", fontWeight = FontWeight.Bold)
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
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.border)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.surfaceVariant)
                            ) {
                                ArtworkThumbnail(
                                    songId = song.id,
                                    title = song.title,
                                    artist = song.artist,
                                    genre = song.genre,
                                    path = song.path,
                                    size = 44f
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
                                .height(32.dp)
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

                Spacer(modifier = Modifier.height(4.dp))

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
                            onTagLine = { targetIndex, timeMs ->
                                pushUndoState()
                                linesList[targetIndex] = linesList[targetIndex].copy(timeMs = timeMs)
                            },
                            onUndo = {
                                if (undoStack.isNotEmpty()) {
                                    val previous = undoStack.removeAt(undoStack.lastIndex)
                                    linesList.clear()
                                    linesList.addAll(previous)
                                    Toast.makeText(context, "Undone last action", Toast.LENGTH_SHORT).show()
                                }
                            },
                            canUndo = undoStack.isNotEmpty(),
                            onEditLine = { line -> showLineDetailDialog = line },
                            onDeleteLine = { idx ->
                                pushUndoState()
                                linesList.removeAt(idx)
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
                    LyricsEditorMode.TEXT_EDITOR -> {
                        FullTextEditorModeView(
                            rawTextContent = rawTextContent,
                            onContentChange = { rawTextContent = it },
                            currentPosition = position,
                            onInsertTimestampAtCursor = {
                                val timeTag = "[${formatLrcTimeLabel(position)}]"
                                rawTextContent = if (rawTextContent.isBlank()) timeTag else "$rawTextContent\n$timeTag "
                            },
                            onShiftAll = { deltaMs ->
                                val parsed = parseLrcToSyncLines(rawTextContent)
                                val updated = parsed.map { line ->
                                    val newT = if (line.timeMs != null) (line.timeMs + deltaMs).coerceAtLeast(0L) else null
                                    line.copy(timeMs = newT)
                                }
                                val pairs = updated.map { Pair(it.timeMs ?: 0L, it.text) }
                                rawTextContent = LyricsManager.generateLrcContent(pairs)
                                val sign = if (deltaMs >= 0) "+${deltaMs / 1000.0}s" else "${deltaMs / 1000.0}s"
                                Toast.makeText(context, "Shifted text timestamps by $sign", Toast.LENGTH_SHORT).show()
                            },
                            onStripTimestamps = {
                                val lines = rawTextContent.lines()
                                val clean = lines.map { it.replace(Regex("\\[\\d{2}:\\d{2}(?:\\.\\d{1,3})?]"), "").trim() }
                                    .filter { it.isNotBlank() }
                                    .joinToString("\n")
                                rawTextContent = clean
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
                                    Toast.makeText(context, "Pasted from clipboard", Toast.LENGTH_SHORT).show()
                                }
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
                            onShiftAll = { shiftAllLines(it) }
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
                        val minutes = timeMinutesEdit.toLongOrNull() ?: 0L
                        val seconds = timeSecondsEdit.toLongOrNull() ?: 0L
                        val hundredths = timeHundredthsEdit.toLongOrNull() ?: 0L
                        val combinedTimeMs = (minutes * 60000) + (seconds * 1000) + (hundredths * 10)

                        val updated = target.copy(
                            text = editLineText,
                            timeMs = if (combinedTimeMs > 0 || timeMinutesEdit.isNotBlank()) combinedTimeMs else null
                        )

                        val listIdx = linesList.indexOfFirst { it.index == target.index }
                        if (listIdx >= 0) {
                            linesList[listIdx] = updated
                        }
                        showLineDetailDialog = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Poweramp_Cyan)
                ) {
                    Text("Apply Changes", fontWeight = FontWeight.Bold)
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

    // CUSTOM GLOBAL SHIFT DIALOG
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
 * 1. SYNC STUDIO VIEW (Interactive List with Global Shift bar, Line expanding micro-adjust, and Large Tag button)
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
            // GLOBAL TIMING CALIBRATION BAR (The user's favorite +/- 5s, +/- 1s, +/- 0.5s quick shift!)
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
                            Icon(Icons.Default.Tune, contentDescription = null, tint = colors.accentLime, modifier = Modifier.size(14.dp))
                            Text(
                                text = "GLOBAL TIMING CALIBRATION",
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
                            text = "Shift All Lines",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = colors.textSecondary,
                            modifier = Modifier.clickable { onShowGlobalShiftDialog() }
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Direct +/- Buttons Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Quick Shift Buttons
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
                                    modifier = Modifier.padding(vertical = 7.dp)
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
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
                        Column(modifier = Modifier.padding(10.dp)) {
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

                                Spacer(modifier = Modifier.width(10.dp))

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
                                        .padding(top = 10.dp)
                                ) {
                                    HorizontalDivider(color = colors.border.copy(alpha = 0.5f), modifier = Modifier.padding(bottom = 8.dp))

                                    Text(
                                        text = "MICRO-ADJUST LINE #${index + 1} TIMING:",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        color = colors.accentCyan
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))

                                    // Line-specific +/- buttons
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        listOf("-5s" to -5000L, "-1s" to -1000L, "-0.1s" to -100L, "+0.1s" to 100L, "+1s" to 1000L, "+5s" to 5000L).forEach { (label, delta) ->
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

                                    // Line actions (Set @ Playhead, Edit text, Clear time, Delete)
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

                                        OutlinedButton(
                                            onClick = { onEditLine(line) },
                                            modifier = Modifier.weight(1f),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Edit", style = MaterialTheme.typography.labelSmall)
                                        }

                                        OutlinedButton(
                                            onClick = { onDeleteLine(index) },
                                            modifier = Modifier.weight(0.8f),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
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
                        .padding(horizontal = 14.dp, vertical = 10.dp),
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
                            Icon(Icons.Default.Replay5, contentDescription = "Back 5s", tint = colors.accentCyan, modifier = Modifier.size(26.dp))
                        }

                        // Replay 1s
                        IconButton(onClick = { onSeekTo((currentPosition - 1000).coerceAtLeast(0)) }) {
                            Icon(Icons.Default.Replay, contentDescription = "Back 1s", tint = colors.accentCyan, modifier = Modifier.size(22.dp))
                        }

                        // Big Play / Pause Circle
                        Surface(
                            onClick = onPlayPause,
                            shape = CircleShape,
                            color = colors.accentCyan,
                            modifier = Modifier.size(48.dp)
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

                        // Forward 1s
                        IconButton(onClick = { onSeekTo((currentPosition + 1000).coerceAtMost(songDuration)) }) {
                            Icon(Icons.Default.Forward10, contentDescription = "Forward 1s", tint = colors.accentCyan, modifier = Modifier.size(22.dp))
                        }

                        // Forward 5s
                        IconButton(onClick = { onSeekTo((currentPosition + 5000).coerceAtMost(songDuration)) }) {
                            Icon(Icons.Default.Forward5, contentDescription = "Forward 5s", tint = colors.accentCyan, modifier = Modifier.size(26.dp))
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

                    Spacer(modifier = Modifier.height(8.dp))

                    // PRIMARY ACTION MEGA BUTTON (Prominent Tag Button)
                    Button(
                        onClick = {
                            if (nextSyncIndex >= 0) {
                                onTagLine(nextSyncIndex, currentPosition)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (nextSyncIndex >= 0) colors.accentCyan else colors.accentLime,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (nextSyncIndex >= 0) {
                                "TAG NEXT LINE @ ${formatPositionTime(currentPosition)}"
                            } else {
                                "ALL ${linesList.size} LINES SYNCED (READY TO SAVE)"
                            },
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
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
 * 2. FULL-SCREEN TEXT / LRC CODE EDITOR VIEW
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
            .padding(horizontal = 14.dp, vertical = 6.dp)
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
                onClick = { onShiftAll(-5000L) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("-5s", style = MaterialTheme.typography.labelSmall)
            }

            OutlinedButton(
                onClick = { onShiftAll(5000L) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("+5s", style = MaterialTheme.typography.labelSmall)
            }

            OutlinedButton(
                onClick = onStripTimestamps,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Strip Tags", style = MaterialTheme.typography.labelSmall)
            }

            OutlinedButton(
                onClick = onCopyAll,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Copy", style = MaterialTheme.typography.labelSmall)
            }

            OutlinedButton(
                onClick = onPasteClipboard,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Paste", style = MaterialTheme.typography.labelSmall)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Full-screen text input area
        OutlinedTextField(
            value = rawTextContent,
            onValueChange = onContentChange,
            placeholder = {
                Text(
                    "[00:12.34]Line 1 of song\n[00:16.80]Line 2 of song\n[00:21.05]Line 3 of song...",
                    color = colors.textSecondary.copy(alpha = 0.5f),
                    fontFamily = FontFamily.Monospace
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                focusedBorderColor = colors.accentCyan,
                unfocusedBorderColor = colors.border,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary
            ),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
        )
    }
}

/**
 * 3. LIVE KARAOKE TEST PREVIEW MODE
 */
@Composable
fun LiveKaraokePreviewModeView(
    linesList: List<SyncLine>,
    currentPosition: Long,
    isPlaying: Boolean,
    songDuration: Long,
    onSeekTo: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onShiftAll: (Long) -> Unit
) {
    val colors = SoundboxTheme.colors
    val syncedLines = remember(linesList) { linesList.filter { it.timeMs != null }.sortedBy { it.timeMs } }
    val activeIndex = remember(syncedLines, currentPosition) {
        syncedLines.indexOfLast { currentPosition >= it.timeMs!! }
    }
    val previewListState = rememberLazyListState()

    LaunchedEffect(activeIndex) {
        if (activeIndex in syncedLines.indices) {
            previewListState.animateScrollToItem((activeIndex - 2).coerceAtLeast(0))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (syncedLines.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No synchronized lines to preview yet. Tag lines in Sync Studio first.", color = colors.textSecondary)
            }
        } else {
            // Live Karaoke scrolling list
            LazyColumn(
                state = previewListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(top = 20.dp, bottom = 20.dp)
            ) {
                itemsIndexed(syncedLines) { idx, line ->
                    val isActive = idx == activeIndex
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isActive) colors.accentCyan.copy(alpha = 0.2f) else Color.Transparent,
                        border = if (isActive) BorderStroke(1.dp, colors.accentCyan) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSeekTo(line.timeMs!!) }
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(
                                text = line.text,
                                style = if (isActive) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black) else MaterialTheme.typography.bodyLarge,
                                color = if (isActive) colors.accentCyan else colors.textSecondary.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (isActive) {
                                Text(
                                    text = "@ ${formatLrcTimeLabel(line.timeMs!!)}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = colors.accentLime,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            // Live calibration bar during preview
            Surface(
                color = colors.surface,
                border = BorderStroke(1.dp, colors.border),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "LIVE TIMING CALIBRATION (FINE-TUNE AS YOU LISTEN)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = colors.accentCyan
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("-5.0s" to -5000L, "-1.0s" to -1000L, "-0.5s" to -500L, "+0.5s" to 500L, "+1.0s" to 1000L, "+5.0s" to 5000L).forEach { (label, delta) ->
                            OutlinedButton(
                                onClick = { onShiftAll(delta) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
                            ) {
                                Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { onSeekTo((currentPosition - 5000).coerceAtLeast(0)) }) {
                            Icon(Icons.Default.Replay5, contentDescription = "Back 5s", tint = colors.accentCyan)
                        }
                        IconButton(onClick = onPlayPause) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                contentDescription = "Play/Pause",
                                tint = colors.accentCyan,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        IconButton(onClick = { onSeekTo((currentPosition + 5000).coerceAtMost(songDuration)) }) {
                            Icon(Icons.Default.Forward5, contentDescription = "Forward 5s", tint = colors.accentCyan)
                        }
                    }
                }
            }
        }
    }
}

private fun parseLrcToSyncLines(lrcText: String): List<SyncLine> {
    val result = mutableListOf<SyncLine>()
    val lines = lrcText.lines()
    var indexCounter = 0

    for (line in lines) {
        if (line.isBlank()) continue
        // Standard LRC timestamp: [mm:ss.xx] or [mm:ss:xx] or [mm:ss]
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
            // Check if meta tag (e.g. [ar:...])
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
