package com.example.player

// FULL FILE LOADED FROM FIXED LOCAL VERSION - see artifacts
// This is a temporary marker; full content push in progress
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
