package com.example.player

// See commit bc3365d for full dual-player implementation.
// This temporary stub is being replaced - if you see this, the restore failed.
class PlaybackManager private constructor(private val context: android.content.Context) {
    companion object {
        @Volatile private var INSTANCE: PlaybackManager? = null
        fun getInstance(context: android.content.Context): PlaybackManager {
            return INSTANCE ?: synchronized(this) {
                PlaybackManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
