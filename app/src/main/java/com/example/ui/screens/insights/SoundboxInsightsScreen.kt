package com.example.ui.screens.insights

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ArtistStat
import com.example.data.model.GenreStat
import com.example.data.model.ListeningMilestone
import com.example.ui.theme.SoundboxTheme
import com.example.ui.viewmodel.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundboxInsightsScreen(
    viewModel: MusicViewModel,
    onNavigateBack: () -> Unit
) {
    val insights by viewModel.insights.collectAsState()
    val colors = SoundboxTheme.colors

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Soundbox Insights",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Listening analytics & achievements",
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
        containerColor = colors.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Playtime & Activity Card
            item {
                HeroPlaytimeCard(
                    totalPlaytimeFormatted = insights.totalPlaytimeHoursFormatted,
                    totalPlayCount = insights.totalPlayCount,
                    artistsCount = insights.uniqueArtistsCount,
                    genresCount = insights.uniqueGenresCount
                )
            }

            // Top Artists Section
            item {
                InsightsSectionCard(
                    title = "Top Artists",
                    subtitle = "Artists you listen to most frequently",
                    icon = Icons.Default.Person
                ) {
                    if (insights.topArtists.isEmpty()) {
                        EmptyStatsNotice("Play tracks to uncover your top artist insights.")
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            insights.topArtists.forEachIndexed { index, stat ->
                                TopArtistRow(rank = index + 1, stat = stat)
                            }
                        }
                    }
                }
            }

            // Top Genres Section
            item {
                InsightsSectionCard(
                    title = "Genre Breakdown",
                    subtitle = "Dominant musical genres in your library",
                    icon = Icons.Default.Category
                ) {
                    if (insights.topGenres.isEmpty()) {
                        EmptyStatsNotice("Add tagged music files to view genre analytics.")
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            insights.topGenres.forEach { stat ->
                                GenreBarRow(stat = stat)
                            }
                        }
                    }
                }
            }

            // Favorite Listening Hours
            item {
                InsightsSectionCard(
                    title = "Favorite Listening Hours",
                    subtitle = "Your daily listening habits breakdown",
                    icon = Icons.Default.AccessTime
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ListeningHabitColumn(
                            label = "Morning",
                            hours = "06:00 - 12:00",
                            percentage = insights.habits.morningPercent,
                            icon = Icons.Default.WbSunny,
                            color = Color(0xFFFFB300),
                            modifier = Modifier.weight(1f)
                        )
                        ListeningHabitColumn(
                            label = "Afternoon",
                            hours = "12:00 - 18:00",
                            percentage = insights.habits.afternoonPercent,
                            icon = Icons.Default.Brightness5,
                            color = Color(0xFFFF7043),
                            modifier = Modifier.weight(1f)
                        )
                        ListeningHabitColumn(
                            label = "Evening",
                            hours = "18:00 - 23:00",
                            percentage = insights.habits.eveningPercent,
                            icon = Icons.Default.NightsStay,
                            color = Color(0xFF7E57C2),
                            modifier = Modifier.weight(1f)
                        )
                        ListeningHabitColumn(
                            label = "Night",
                            hours = "23:00 - 06:00",
                            percentage = insights.habits.lateNightPercent,
                            icon = Icons.Default.Bedtime,
                            color = Color(0xFF29B6F6),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Weekly Milestones & Achievements
            item {
                InsightsSectionCard(
                    title = "Audiophile Milestones",
                    subtitle = "Weekly and library listening badges",
                    icon = Icons.Default.EmojiEvents
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        insights.milestones.forEach { milestone ->
                            MilestoneRow(milestone = milestone)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HeroPlaytimeCard(
    totalPlaytimeFormatted: String,
    totalPlayCount: Int,
    artistsCount: Int,
    genresCount: Int
) {
    val colors = SoundboxTheme.colors

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        colors.accentCyan.copy(alpha = 0.22f),
                        colors.accentAmber.copy(alpha = 0.18f),
                        colors.surfaceElevated
                    )
                )
            )
            .border(
                width = 1.dp,
                color = colors.accentCyan.copy(alpha = 0.35f),
                shape = RoundedCornerShape(20.dp)
            )
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
                    color = colors.accentCyan.copy(alpha = 0.2f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Insights,
                            contentDescription = null,
                            tint = colors.accentCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Text(
                    text = "TOTAL PLAYTIME",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    ),
                    color = colors.accentCyan
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = totalPlaytimeFormatted,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 34.sp
                ),
                color = colors.textPrimary
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = colors.borderSubtle.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatMetricColumn(label = "Total Plays", value = "$totalPlayCount")
                StatMetricColumn(label = "Artists", value = "$artistsCount")
                StatMetricColumn(label = "Genres", value = "$genresCount")
            }
        }
    }
}

@Composable
fun StatMetricColumn(label: String, value: String) {
    val colors = SoundboxTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = colors.textPrimary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary
        )
    }
}

@Composable
fun InsightsSectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = SoundboxTheme.colors

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colors.accentAmber.copy(alpha = 0.15f),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = colors.accentAmber,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.textPrimary
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
fun TopArtistRow(rank: Int, stat: ArtistStat) {
    val colors = SoundboxTheme.colors

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = if (rank == 1) Color(0xFFFFD700).copy(alpha = 0.2f) else colors.surfaceElevated,
            modifier = Modifier.size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "#$rank",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (rank == 1) Color(0xFFFFD700) else colors.textSecondary
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stat.artistName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${stat.playCount} plays",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.accentCyan
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { stat.percentage.coerceIn(0.05f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(CircleShape),
                color = if (rank == 1) colors.accentAmber else colors.accentCyan,
                trackColor = colors.borderSubtle
            )
        }
    }
}

@Composable
fun GenreBarRow(stat: GenreStat) {
    val colors = SoundboxTheme.colors

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stat.genreName,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary
            )
            Text(
                text = "${(stat.percentage * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = colors.accentCyan
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { stat.percentage.coerceIn(0.05f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape),
            color = colors.accentCyan,
            trackColor = colors.borderSubtle
        )
    }
}

@Composable
fun ListeningHabitColumn(
    label: String,
    hours: String,
    percentage: Int,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    val colors = SoundboxTheme.colors

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.surfaceElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "$percentage%",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = colors.textSecondary,
                maxLines = 1
            )
        }
    }
}

@Composable
fun MilestoneRow(milestone: ListeningMilestone) {
    val colors = SoundboxTheme.colors

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.surfaceElevated,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (milestone.isAchieved) Color(0xFFFFD700).copy(alpha = 0.5f) else colors.borderSubtle
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = if (milestone.isAchieved) Color(0xFFFFD700).copy(alpha = 0.2f) else colors.surface,
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (milestone.isAchieved) Icons.Default.CheckCircle else Icons.Default.Stars,
                        contentDescription = null,
                        tint = if (milestone.isAchieved) Color(0xFFFFD700) else colors.textMuted,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = milestone.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.textPrimary
                    )
                    Text(
                        text = "${milestone.currentFormatted} / ${milestone.targetFormatted}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (milestone.isAchieved) Color(0xFFFFD700) else colors.textSecondary
                    )
                }
                Text(
                    text = milestone.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { milestone.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape),
                    color = if (milestone.isAchieved) Color(0xFFFFD700) else colors.accentCyan,
                    trackColor = colors.borderSubtle
                )
            }
        }
    }
}

@Composable
fun EmptyStatsNotice(message: String) {
    val colors = SoundboxTheme.colors
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = colors.textSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        textAlign = TextAlign.Center
    )
}
