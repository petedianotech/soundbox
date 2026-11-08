package com.example.player

// FULL FILE LOADED FROM LOCAL PATCHED VERSION
// If you see this comment only, the large-file push failed and needs retry.
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
