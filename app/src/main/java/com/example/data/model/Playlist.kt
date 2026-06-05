package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val songIds: List<String> = emptyList(), // Store the list of song IDs
    val dateCreated: Long = System.currentTimeMillis()
)

// Zero-dependency Converter for Room
class PlaylistConverters {
    @TypeConverter
    fun fromSongIdsList(value: List<String>?): String {
        return value?.joinToString(separator = "|") ?: ""
    }

    @TypeConverter
    fun toSongIdsList(value: String?): List<String> {
        if (value.isNullOrEmpty()) return emptyList()
        return value.split("|").filter { it.isNotEmpty() }
    }
}
