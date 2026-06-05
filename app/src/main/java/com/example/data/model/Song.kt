package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.File

@Entity(tableName = "songs")
data class Song(
    @PrimaryKey val id: String, // Can be MediaStore ID or synthetic ID
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long, // in milliseconds
    val path: String, // Full file path or synthetic URI
    val size: Long, // in bytes
    val folderPath: String, // Dir contains the song
    val folderName: String, // User-visible parent folder
    val trackNumber: Int = 0,
    val genre: String = "Unknown",
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedTime: Long = 0L,
    val dateAdded: Long = System.currentTimeMillis()
) {
    // Utility representation of file structure
    fun getParentFolderName(): String {
        return try {
            File(path).parentFile?.name ?: "Root"
        } catch (e: Exception) {
            folderName
        }
    }
}
