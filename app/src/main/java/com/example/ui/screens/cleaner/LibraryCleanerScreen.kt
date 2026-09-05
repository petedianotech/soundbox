package com.example.ui.screens.cleaner

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DuplicateGroup
import com.example.data.model.Song
import com.example.ui.theme.SoundboxTheme
import com.example.ui.viewmodel.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryCleanerScreen(
    viewModel: MusicViewModel,
    onNavigateBack: () -> Unit
) {
    val summary by viewModel.cleanerSummary.collectAsState()
    val colors = SoundboxTheme.colors
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Duplicates, 1: Low Quality
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            snackbarMessage = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Library Cleaner",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Duplicates & low-bitrate audio cleaner",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.topBarBackground
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = colors.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Cleaner Hero Card
            item {
                CleanerHeroCard(
                    cleanableSize = summary.formattedCleanableSize,
                    duplicatesCount = summary.duplicateGroups.size,
                    lowQualityCount = summary.lowQualityTracks.size,
                    onCleanAllDuplicates = {
                        if (summary.duplicateGroups.isNotEmpty()) {
                            viewModel.cleanAllDuplicates()
                            snackbarMessage = "Cleaned duplicate audio tracks (kept highest quality copies)"
                        }
                    }
                )
            }

            // Tab Row
            item {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = colors.surfaceVariant,
                    contentColor = colors.accentCyan,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = colors.accentCyan
                        )
                    },
                    divider = {
                        HorizontalDivider(color = colors.borderSubtle)
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                text = "Duplicates (${summary.duplicateGroups.size})",
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 0) colors.accentCyan else colors.textSecondary
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                text = "Low-Quality (${summary.lowQualityTracks.size})",
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 1) colors.accentCyan else colors.textSecondary
                            )
                        }
                    )
                }
            }

            // Tab Content
            if (selectedTab == 0) {
                if (summary.duplicateGroups.isEmpty()) {
                    item {
                        EmptyCleanState(
                            title = "No Duplicates Detected",
                            description = "Your library is clean! No duplicate recordings found.",
                            icon = Icons.Default.CheckCircle
                        )
                    }
                } else {
                    items(summary.duplicateGroups, key = { it.key }) { group ->
                        DuplicateGroupCard(
                            group = group,
                            onResolveGroup = {
                                viewModel.cleanDuplicateGroup(group, keepBest = true)
                                snackbarMessage = "Resolved duplicate for '${group.title}'"
                            }
                        )
                    }
                }
            } else {
                if (summary.lowQualityTracks.isEmpty()) {
                    item {
                        EmptyCleanState(
                            title = "Audiophile Ready",
                            description = "All library audio tracks meet high bitrate standards.",
                            icon = Icons.Default.HighQuality
                        )
                    }
                } else {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${summary.lowQualityTracks.size} Low-bitrate tracks",
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.textSecondary
                            )
                            Button(
                                onClick = {
                                    viewModel.cleanLowQualityTracks(summary.lowQualityTracks)
                                    snackbarMessage = "Removed ${summary.lowQualityTracks.size} low-quality tracks"
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.accentAmber,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Remove All", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }

                    items(summary.lowQualityTracks, key = { it.id }) { song ->
                        LowQualityTrackCard(
                            song = song,
                            onDelete = {
                                viewModel.deleteSongs(listOf(song))
                                snackbarMessage = "Removed '${song.title}'"
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CleanerHeroCard(
    cleanableSize: String,
    duplicatesCount: Int,
    lowQualityCount: Int,
    onCleanAllDuplicates: () -> Unit
) {
    val colors = SoundboxTheme.colors

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        colors.accentAmber.copy(alpha = 0.20f),
                        colors.accentCyan.copy(alpha = 0.15f),
                        colors.surfaceElevated
                    )
                )
            )
            .border(1.dp, colors.accentAmber.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = colors.accentAmber.copy(alpha = 0.2f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CleaningServices,
                            contentDescription = null,
                            tint = colors.accentAmber,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Text(
                    text = "POTENTIAL STORAGE SAVINGS",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    ),
                    color = colors.accentAmber
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = cleanableSize,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 32.sp
                ),
                color = colors.textPrimary
            )

            Text(
                text = "$duplicatesCount duplicate groups • $lowQualityCount low-bitrate recordings",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )

            if (duplicatesCount > 0) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onCleanAllDuplicates,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentAmber,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoFixHigh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Auto-Clean Duplicates (Keep Best Quality)",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun DuplicateGroupCard(
    group: DuplicateGroup,
    onResolveGroup: () -> Unit
) {
    val colors = SoundboxTheme.colors

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${group.artist} • ${group.count} duplicate copies",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Button(
                    onClick = onResolveGroup,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentCyan,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("Keep Best", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = colors.borderSubtle.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                group.duplicates.forEachIndexed { idx, song ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Copy #${idx + 1}: ${song.bitrateKbps}kbps • ${(song.size / (1024 * 1024.0)).let { String.format("%.1f MB", it) }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary
                        )
                        Text(
                            text = song.folderName,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMuted,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LowQualityTrackCard(
    song: Song,
    onDelete: () -> Unit
) {
    val colors = SoundboxTheme.colors

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${song.artist} • ${song.bitrateKbps} kbps • ${(song.size / (1024 * 1024.0)).let { String.format("%.1f MB", it) }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.accentAmber,
                    maxLines = 1
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete track",
                    tint = colors.textSecondary
                )
            }
        }
    }
}

@Composable
fun EmptyCleanState(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    val colors = SoundboxTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.accentCyan,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = colors.textPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
