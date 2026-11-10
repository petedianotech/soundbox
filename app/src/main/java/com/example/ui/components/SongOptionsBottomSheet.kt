package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Song
import com.example.ui.theme.SoundboxTheme
import com.example.ui.viewmodel.MusicViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongOptionsBottomSheet(
    song: Song,
    viewModel: MusicViewModel,
    onDismiss: () -> Unit,
    onNavigateToLyrics: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val colors = SoundboxTheme.colors

    var showRatingDialog by remember { mutableStateOf(false) }
    var showEditTagsDialog by remember { mutableStateOf(false) }
    var showSongDetailsDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceElevated,
        contentColor = colors.textPrimary,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header Track Preview
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArtworkThumbnail(
                    songId = song.id,
                    title = song.title,
                    artist = song.artist,
                    genre = song.genre,
                    path = song.path,
                    size = 52f
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${song.artist} • ${song.album}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (song.rating > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFFFD700).copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "${song.rating}★",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFFFD700)
                        )
                    }
                }
            }

            Divider(color = colors.border, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(6.dp))

            // 1. Play Next
            DropdownMenuItem(
                text = { Text("Play Next") },
                leadingIcon = { Icon(Icons.Default.PlaylistPlay, contentDescription = null, tint = colors.accentCyan) },
                onClick = {
                    viewModel.playNext(song)
                    onDismiss()
                    Toast.makeText(context, "Added to play next", Toast.LENGTH_SHORT).show()
                }
            )

            // 2. Add to Queue
            DropdownMenuItem(
                text = { Text("Add to Queue") },
                leadingIcon = { Icon(Icons.Default.Queue, contentDescription = null, tint = colors.accentCyan) },
                onClick = {
                    viewModel.addToQueue(song)
                    onDismiss()
                    Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
                }
            )

            // 3. Like / Favorite Toggle
            DropdownMenuItem(
                text = { Text(if (song.isFavorite) "Remove from Liked" else "Add to Liked") },
                leadingIcon = {
                    Icon(
                        if (song.isFavorite) Icons.Default.ThumbUp else Icons.Default.ThumbUpOffAlt,
                        contentDescription = null,
                        tint = colors.accentCyan
                    )
                },
                onClick = {
                    viewModel.toggleFavorite(song)
                    onDismiss()
                }
            )

            // 4. Rate Song (1-5 Stars)
            DropdownMenuItem(
                text = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Rate Track")
                        Text(
                            text = if (song.rating > 0) "${song.rating} / 5 ★" else "Unrated",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (song.rating > 0) Color(0xFFFFD700) else colors.textMuted
                        )
                    }
                },
                leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700)) },
                onClick = {
                    showRatingDialog = true
                }
            )

            // 5. Edit Meta Tags (ID3)
            DropdownMenuItem(
                text = { Text("Edit Meta Tags") },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = colors.accentCyan) },
                onClick = {
                    showEditTagsDialog = true
                }
            )

            // 6. Lyrics Studio
            if (onNavigateToLyrics != null) {
                DropdownMenuItem(
                    text = { Text("Lyrics Sync Studio") },
                    leadingIcon = { Icon(Icons.Default.EditNote, contentDescription = null, tint = colors.accentCyan) },
                    onClick = {
                        onDismiss()
                        onNavigateToLyrics()
                    }
                )
            }

            // 7. Song Info & Technical Details
            DropdownMenuItem(
                text = { Text("Track Details & Info") },
                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = colors.accentCyan) },
                onClick = {
                    showSongDetailsDialog = true
                }
            )

            Divider(color = colors.border, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

            // 8. Delete Track
            DropdownMenuItem(
                text = { Text("Delete Track from Device", color = Color(0xFFFF5252), fontWeight = FontWeight.SemiBold) },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF5252)) },
                onClick = {
                    showDeleteConfirmDialog = true
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // --- RATING DIALOG ---
    if (showRatingDialog) {
        var currentRating by remember { mutableIntStateOf(song.rating) }

        AlertDialog(
            containerColor = colors.dialogBackground,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
            onDismissRequest = { showRatingDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700))
                    Text("Rate Track", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        (1..5).forEach { star ->
                            IconButton(onClick = { currentRating = if (currentRating == star) 0 else star }) {
                                Icon(
                                    imageVector = if (star <= currentRating) Icons.Default.Star else Icons.Outlined.StarBorder,
                                    contentDescription = "$star stars",
                                    tint = if (star <= currentRating) Color(0xFFFFD700) else colors.textMuted,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (currentRating > 0) "$currentRating of 5 Stars" else "Tap stars to rate (0 = unrated)",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.accentCyan
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateSongRating(song, currentRating)
                        showRatingDialog = false
                        onDismiss()
                        Toast.makeText(context, "Rating updated to $currentRating★", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.accentCyan)
                ) {
                    Text("Save Rating", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRatingDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }

    // --- EDIT META TAGS DIALOG ---
    if (showEditTagsDialog) {
        var titleInput by remember { mutableStateOf(song.title) }
        var artistInput by remember { mutableStateOf(song.artist) }
        var albumInput by remember { mutableStateOf(song.album) }
        var genreInput by remember { mutableStateOf(song.genre) }
        var trackNumInput by remember { mutableStateOf(if (song.trackNumber > 0) song.trackNumber.toString() else "") }

        AlertDialog(
            containerColor = colors.dialogBackground,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
            onDismissRequest = { showEditTagsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = colors.accentCyan)
                    Text("Edit Audio Tags", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        label = { Text("Track Title") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accentCyan,
                            unfocusedBorderColor = colors.border,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        )
                    )

                    OutlinedTextField(
                        value = artistInput,
                        onValueChange = { artistInput = it },
                        label = { Text("Artist Name") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accentCyan,
                            unfocusedBorderColor = colors.border,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        )
                    )

                    OutlinedTextField(
                        value = albumInput,
                        onValueChange = { albumInput = it },
                        label = { Text("Album Title") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accentCyan,
                            unfocusedBorderColor = colors.border,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = genreInput,
                            onValueChange = { genreInput = it },
                            label = { Text("Genre") },
                            modifier = Modifier.weight(1.2f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.accentCyan,
                                unfocusedBorderColor = colors.border,
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary
                            )
                        )

                        OutlinedTextField(
                            value = trackNumInput,
                            onValueChange = { if (it.length <= 3 && it.all { c -> c.isDigit() }) trackNumInput = it },
                            label = { Text("Track #") },
                            modifier = Modifier.weight(0.8f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.accentCyan,
                                unfocusedBorderColor = colors.border,
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary
                            )
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val updatedSong = song.copy(
                            title = titleInput.trim().ifEmpty { song.title },
                            artist = artistInput.trim().ifEmpty { song.artist },
                            album = albumInput.trim().ifEmpty { song.album },
                            genre = genreInput.trim(),
                            trackNumber = trackNumInput.toIntOrNull() ?: song.trackNumber
                        )
                        viewModel.updateSongMetadata(updatedSong)
                        showEditTagsDialog = false
                        onDismiss()
                        Toast.makeText(context, "Saved meta tag edits!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.accentCyan)
                ) {
                    Text("Save Tags", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditTagsDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }

    // --- SONG DETAILS DIALOG ---
    if (showSongDetailsDialog) {
        val file = File(song.path)
        val sizeMb = if (file.exists()) String.format(Locale.US, "%.2f MB", file.length() / (1024.0 * 1024.0)) else "Unknown Size"
        val ext = if (song.path.contains(".")) song.path.substringAfterLast(".").uppercase() else "AUDIO"
        val addedDate = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(Date(song.dateAdded * 1000L))

        AlertDialog(
            containerColor = colors.dialogBackground,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
            onDismissRequest = { showSongDetailsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = colors.accentCyan)
                    Text("Track Metadata & Info", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    DetailRow("Title", song.title)
                    DetailRow("Artist", song.artist)
                    DetailRow("Album", song.album)
                    DetailRow("Genre", song.genre.ifEmpty { "Audio Track" })
                    DetailRow("Format", "$ext • $sizeMb")
                    DetailRow("Duration", formatDurationLabel(song.duration))
                    DetailRow("Play Count", "${song.playCount} plays")
                    DetailRow("Rating", if (song.rating > 0) "${song.rating} Stars" else "Unrated")
                    DetailRow("Date Added", addedDate)
                    DetailRow("File Path", song.path, isMonospace = true)
                }
            },
            confirmButton = {
                TextButton(onClick = { showSongDetailsDialog = false }) {
                    Text("Close", color = colors.accentCyan, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // --- DELETE CONFIRMATION DIALOG ---
    if (showDeleteConfirmDialog) {
        AlertDialog(
            containerColor = colors.dialogBackground,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF5252))
                    Text("Delete Track Permanently", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text("Are you sure you want to delete \"${song.title}\"?")
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "This will permanently delete the audio file from your device storage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSongFromDevice(song) {
                            Toast.makeText(context, "Track deleted", Toast.LENGTH_SHORT).show()
                        }
                        showDeleteConfirmDialog = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF5252))
                ) {
                    Text("Delete Permanently", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String, isMonospace: Boolean = false) {
    val colors = SoundboxTheme.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            ),
            color = colors.accentCyan
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default
            ),
            color = colors.textPrimary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatDurationLabel(ms: Long): String {
    val sec = (ms / 1000) % 60
    val min = (ms / (1000 * 60)) % 60
    val hr = ms / (1000 * 60 * 60)
    return if (hr > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hr, min, sec)
    } else {
        String.format(Locale.US, "%d:%02d", min, sec)
    }
}
