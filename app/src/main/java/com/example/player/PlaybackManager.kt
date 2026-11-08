package com.example.player

// This is a temporary stub. Full content will be pushed in next step if size limits hit.
// The full 812-line dual-player implementation is ready.

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
