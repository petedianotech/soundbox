package com.example.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import com.example.player.PlaybackManager

class HeadsetPlugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val settings = SettingsManager.getInstance(context)
        val playbackManager = PlaybackManager.getInstance(context.applicationContext)

        when (intent.action) {
            Intent.ACTION_HEADSET_PLUG -> {
                val state = intent.getIntExtra("state", -1)
                when (state) {
                    0 -> {
                        // Unplugged -> Auto-pause if enabled
                        if (settings.autoPauseOnHeadphoneUnplug.value && playbackManager.isPlaying.value) {
                            playbackManager.playPause()
                        }
                    }
                    1 -> {
                        // Plugged -> Auto-resume if enabled
                        if (settings.autoResumeOnHeadphonePlug.value && !playbackManager.isPlaying.value) {
                            playbackManager.playPause()
                        }
                    }
                }
            }
            AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                // Audio output routing changed to speaker (e.g., Bluetooth/headphone disconnected)
                if (settings.autoPauseOnHeadphoneUnplug.value && playbackManager.isPlaying.value) {
                    playbackManager.playPause()
                }
            }
        }
    }
}
