package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.example.data.model.Song
import com.example.util.OnlineCoverFetcher
import com.example.util.OnlineCoverResult
import kotlinx.coroutines.launch

@Composable
fun OnlineCoverDialog(
    song: Song,
    onDismissRequest: () -> Unit,
    onCoverUpdated: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("${song.artist} ${song.title}".trim()) }
    var results by remember { mutableStateOf<List<OnlineCoverResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var downloadingUrl by remember { mutableStateOf<String?>(null) }
    var hasCustomCover by remember { mutableStateOf(OnlineCoverFetcher.hasSavedCover(context, song.id)) }

    fun performSearch() {
        if (searchQuery.isBlank()) return
        isLoading = true
        coroutineScope.launch {
            results = OnlineCoverFetcher.searchCoverArt(song.artist, searchQuery)
            isLoading = false
        }
    }

    LaunchedEffect(song) {
        performSearch()
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(28.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Download Online Album Art",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Search HD album covers from web (iTunes & Deezer) for \"${song.title}\":",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Artist Song Title") },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { performSearch() }) {
                                Icon(Icons.Default.Search, contentDescription = "Search")
                            }
                        },
                        shape = CircleShape
                    )
                }

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Text(
                                "Searching web for album art...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (results.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.ImageSearch,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Text(
                                "No online cover art found.\nTry refining your search terms above.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(results) { item ->
                            val isDownloadingThis = downloadingUrl == item.hdImageUrl
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = downloadingUrl == null) {
                                        downloadingUrl = item.hdImageUrl
                                        coroutineScope.launch {
                                            val saved = OnlineCoverFetcher.downloadAndSaveCover(
                                                context = context,
                                                songId = song.id,
                                                imageUrl = item.hdImageUrl
                                            )
                                            downloadingUrl = null
                                            if (saved != null) {
                                                Toast.makeText(context, "HD Cover downloaded & applied!", Toast.LENGTH_SHORT).show()
                                                hasCustomCover = true
                                                onCoverUpdated()
                                                onDismissRequest()
                                            } else {
                                                Toast.makeText(context, "Failed to download cover image", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(110.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                    ) {
                                        SubcomposeAsyncImage(
                                            model = item.hdImageUrl,
                                            contentDescription = item.title,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                            loading = {
                                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                                }
                                            }
                                        )

                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(4.dp)
                                        ) {
                                            Text(
                                                text = item.source,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }

                                        if (isDownloadingThis) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = item.album.ifBlank { item.title },
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = item.artist,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                if (hasCustomCover) {
                    TextButton(
                        onClick = {
                            OnlineCoverFetcher.removeSavedCover(context, song.id)
                            hasCustomCover = false
                            Toast.makeText(context, "Custom cover removed", Toast.LENGTH_SHORT).show()
                            onCoverUpdated()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Remove Custom Online Cover")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Close")
            }
        }
    )
}
