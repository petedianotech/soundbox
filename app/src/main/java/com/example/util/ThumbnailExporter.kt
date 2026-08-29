package com.example.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import com.example.data.model.Song
import com.example.ui.components.getDefaultThumbnailResId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object ThumbnailExporter {

    suspend fun exportSongThumbnail(context: Context, song: Song): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Obtain bitmap from embedded artwork or default neutral resource
                val bitmap: Bitmap = getArtworkBitmap(context, song) ?: run {
                    val fallbackRes = getDefaultThumbnailResId(song.id.ifEmpty { song.title })
                    BitmapFactory.decodeResource(context.resources, fallbackRes)
                } ?: return@withContext false

                val safeFileName = sanitizeFileName(song.title.ifEmpty { "song" })
                val fileName = "Soundbox_${safeFileName}_${System.currentTimeMillis()}.jpg"

                var success = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Soundbox")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }

                    val uri: Uri? = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    if (uri != null) {
                        context.contentResolver.openOutputStream(uri)?.use { outputStream: OutputStream ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                        }
                        values.clear()
                        values.put(MediaStore.Images.Media.IS_PENDING, 0)
                        context.contentResolver.update(uri, values, null, null)
                        success = true
                    }
                } else {
                    val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    val soundboxDir = File(picturesDir, "Soundbox").apply { mkdirs() }
                    val imageFile = File(soundboxDir, fileName)
                    FileOutputStream(imageFile).use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                    }
                    success = true
                }

                withContext(Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(context, "Thumbnail saved to Pictures/Soundbox", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to save thumbnail", Toast.LENGTH_SHORT).show()
                    }
                }
                success
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error saving thumbnail: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                false
            }
        }
    }

    private fun getArtworkBitmap(context: Context, song: Song): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            if (song.path.startsWith("content://")) {
                retriever.setDataSource(context, Uri.parse(song.path))
            } else {
                retriever.setDataSource(song.path)
            }
            val artBytes = retriever.embeddedPicture
            if (artBytes != null && artBytes.isNotEmpty()) {
                BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {}
        }
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9_.-]"), "_").take(32)
    }
}
