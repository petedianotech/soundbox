package com.example.data.model

data class ListeningMilestone(
    val id: String,
    val title: String,
    val description: String,
    val progress: Float, // 0.0f to 1.0f
    val currentFormatted: String,
    val targetFormatted: String,
    val isAchieved: Boolean,
    val badgeIconName: String = "Star"
)

data class ListeningHabits(
    val morningPercent: Int,    // 06:00 - 12:00
    val afternoonPercent: Int,  // 12:00 - 18:00
    val eveningPercent: Int,    // 18:00 - 23:00
    val lateNightPercent: Int   // 23:00 - 06:00
)

data class SoundboxInsights(
    val totalPlayCount: Int,
    val totalPlaytimeMs: Long,
    val uniqueArtistsCount: Int,
    val uniqueGenresCount: Int,
    val topArtists: List<ArtistStat>,
    val topGenres: List<GenreStat>,
    val habits: ListeningHabits,
    val milestones: List<ListeningMilestone>
) {
    val totalPlaytimeHoursFormatted: String
        get() {
            val hours = totalPlaytimeMs / (1000 * 60 * 60)
            val minutes = (totalPlaytimeMs / (1000 * 60)) % 60
            return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
        }
}

data class ArtistStat(
    val artistName: String,
    val playCount: Int,
    val trackCount: Int,
    val percentage: Float
)

data class GenreStat(
    val genreName: String,
    val playCount: Int,
    val percentage: Float
)
