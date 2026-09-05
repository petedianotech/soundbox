package com.example.util

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.util.Log
import com.example.player.PlaybackManager

class HeadsetPlugReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "HeadsetPlugReceiver"
        private var lastWiredState: Int = -1
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val settings = SettingsManager.getInstance(context)
        val playbackManager = PlaybackManager.getInstance(context.applicationContext)

        Log.d(TAG, "Received audio routing action: $action")

        when (action) {
            Intent.ACTION_HEADSET_PLUG -> {
                val state = intent.getIntExtra("state", -1)
                val isSticky = isInitialStickyBroadcast

                // If this is the initial sticky broadcast upon registration, record state without firing resume
                if (isSticky) {
                    lastWiredState = state
                    Log.d(TAG, "Initial sticky headset state: $state (no action taken)")
                    return
                }

                // If the state has not actually changed, ignore redundant triggers
                if (lastWiredState == state && state != -1) {
                    return
                }
                lastWiredState = state

                when (state) {
                    0 -> {
                        // Unplugged -> Auto-pause if enabled
                        Log.d(TAG, "Headset unplugged event detected")
                        if (settings.autoPauseOnHeadphoneUnplug.value) {
                            playbackManager.pauseOnHeadsetDisconnected()
                        }
                    }
                    1 -> {
                        // Plugged in -> Auto-resume if enabled
                        Log.d(TAG, "Headset plugged in event detected")
                        if (settings.autoResumeOnHeadphonePlug.value) {
                            playbackManager.resumeOnHeadsetConnected()
                        }
                    }
                }
            }

            AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                // Audio routed to speaker (headphones/BT disconnected)
                Log.d(TAG, "Audio becoming noisy: pausing playback")
                if (settings.autoPauseOnHeadphoneUnplug.value) {
                    playbackManager.pauseOnHeadsetDisconnected()
                }
            }

            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                Log.d(TAG, "Bluetooth ACL connected: evaluating auto-resume")
                if (settings.autoResumeOnHeadphonePlug.value) {
                    playbackManager.resumeOnHeadsetConnected()
                }
            }

            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                Log.d(TAG, "Bluetooth ACL disconnected: evaluating auto-pause")
                if (settings.autoPauseOnHeadphoneUnplug.value) {
                    playbackManager.pauseOnHeadsetDisconnected()
                }
            }

            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)
                if (state == BluetoothProfile.STATE_CONNECTED) {
                    Log.d(TAG, "Bluetooth A2DP connected: evaluating auto-resume")
                    if (settings.autoResumeOnHeadphonePlug.value) {
                        playbackManager.resumeOnHeadsetConnected()
                    }
                } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d(TAG, "Bluetooth A2DP disconnected: evaluating auto-pause")
                    if (settings.autoPauseOnHeadphoneUnplug.value) {
                        playbackManager.pauseOnHeadsetDisconnected()
                    }
                }
            }
        }
    }
}
