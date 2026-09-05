package com.example.data.model

data class DuplicateGroup(
    val key: String,
    val title: String,
    val artist: String,
    val duplicates: List<Song>
) {
    val count: Int get() = duplicates.size
    val totalWastedBytes: Long get() {
        if (duplicates.size <= 1) return 0L
        val sorted = duplicates.sortedByDescending { it.size }
        return sorted.drop(1).sumOf { it.size }
    }
}

data class CleanerSummary(
    val duplicateGroups: List<DuplicateGroup>,
    val lowQualityTracks: List<Song>,
    val totalDuplicateWasteBytes: Long,
    val totalLowQualityBytes: Long
) {
    val totalCleanableBytes: Long get() = totalDuplicateWasteBytes + totalLowQualityBytes
    
    val formattedCleanableSize: String
        get() {
            val bytes = totalCleanableBytes
            if (bytes <= 0) return "0 MB"
            val mb = bytes / (1024.0 * 1024.0)
            return if (mb >= 1024) {
                String.format("%.2f GB", mb / 1024.0)
            } else {
                String.format("%.1f MB", mb)
            }
        }
}
