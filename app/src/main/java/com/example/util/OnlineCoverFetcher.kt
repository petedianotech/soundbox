package com.example.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class OnlineCoverResult(
    val title: String,
    val artist: String,
    val album: String,
    val thumbnailUrl: String,
    val hdImageUrl: String,
    val source: String
)

object OnlineCoverFetcher {

    fun getSavedCoverFile(context: Context, songId: String): File {
        val coversDir = File(context.filesDir, "covers")
        if (!coversDir.exists()) {
            coversDir.mkdirs()
        }
        return File(coversDir, "$songId.jpg")
    }

    fun hasSavedCover(context: Context, songId: String): Boolean {
        return getSavedCoverFile(context, songId).exists()
    }

    fun removeSavedCover(context: Context, songId: String): Boolean {
        val file = getSavedCoverFile(context, songId)
        return if (file.exists()) file.delete() else true
    }

    suspend fun searchCoverArt(artist: String, title: String): List<OnlineCoverResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<OnlineCoverResult>()
        val cleanArtist = artist.replace("(?i)\\b(feat|ft|featuring|x|,|&)\\b.*".toRegex(), "").trim()
        val cleanTitle = title.replace("\\s*\\([^)]*\\)".toRegex(), "").replace("\\s*\\[[^]]*\\]".toRegex(), "").trim()
        val query = "$cleanArtist $cleanTitle".trim().ifBlank { title }

        // 1. iTunes API Search
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val urlStr = "https://itunes.apple.com/search?term=$encodedQuery&entity=song&limit=10"
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.connectTimeout = 7000
            conn.readTimeout = 7000
            if (conn.responseCode == 200) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonObj = JSONObject(jsonStr)
                val array = jsonObj.optJSONArray("results")
                if (array != null) {
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        val itemTitle = item.optString("trackName", "")
                        val itemArtist = item.optString("artistName", "")
                        val itemAlbum = item.optString("collectionName", "")
                        val art100 = item.optString("artworkUrl100", "")
                        if (art100.isNotBlank()) {
                            val artHd = art100.replace("100x100bb", "600x600bb")
                            results.add(
                                OnlineCoverResult(
                                    title = itemTitle,
                                    artist = itemArtist,
                                    album = itemAlbum,
                                    thumbnailUrl = art100,
                                    hdImageUrl = artHd,
                                    source = "iTunes"
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("OnlineCoverFetcher", "iTunes search error: ${e.message}")
        }

        // 2. Deezer API Search
        if (results.size < 5) {
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val urlStr = "https://api.deezer.com/search?q=$encodedQuery&limit=10"
                val conn = URL(urlStr).openConnection() as HttpURLConnection
                conn.connectTimeout = 7000
                conn.readTimeout = 7000
                if (conn.responseCode == 200) {
                    val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                    val jsonObj = JSONObject(jsonStr)
                    val array = jsonObj.optJSONArray("data")
                    if (array != null) {
                        for (i in 0 until array.length()) {
                            val item = array.getJSONObject(i)
                            val itemTitle = item.optString("title", "")
                            val artistObj = item.optJSONObject("artist")
                            val itemArtist = artistObj?.optString("name", "") ?: ""
                            val albumObj = item.optJSONObject("album")
                            val itemAlbum = albumObj?.optString("title", "") ?: ""
                            val coverXl = albumObj?.optString("cover_xl", "") ?: ""
                            val coverBig = albumObj?.optString("cover_big", "") ?: ""
                            val coverMed = albumObj?.optString("cover_medium", "") ?: ""
                            val hd = coverXl.ifBlank { coverBig.ifBlank { coverMed } }
                            if (hd.isNotBlank()) {
                                results.add(
                                    OnlineCoverResult(
                                        title = itemTitle,
                                        artist = itemArtist,
                                        album = itemAlbum,
                                        thumbnailUrl = coverMed.ifBlank { hd },
                                        hdImageUrl = hd,
                                        source = "Deezer"
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("OnlineCoverFetcher", "Deezer search error: ${e.message}")
            }
        }

        results.distinctBy { it.hdImageUrl }
    }

    suspend fun downloadAndSaveCover(context: Context, songId: String, imageUrl: String): File? = withContext(Dispatchers.IO) {
        try {
            val targetFile = getSavedCoverFile(context, songId)
            val conn = URL(imageUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            if (conn.responseCode == 200) {
                conn.inputStream.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                targetFile
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("OnlineCoverFetcher", "Download cover error: ${e.message}")
            null
        }
    }
}
