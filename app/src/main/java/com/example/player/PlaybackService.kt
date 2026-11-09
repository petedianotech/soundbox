package com.example.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.Futures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.example.MainActivity
import com.example.R

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        const val CHANNEL_ID = "music_playback_channel"
        const val ACTION_TOGGLE_FAVORITE = "ACTION_TOGGLE_FAVORITE"
        const val ACTION_FORWARD_10 = "ACTION_FORWARD_10"
        const val ACTION_REWIND_10 = "ACTION_REWIND_10"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Bind to the active ExoPlayer instance in PlaybackManager
        val pbManager = PlaybackManager.getInstance(this)
        val initialPlayer = pbManager.player

        pbManager.onActivePlayerChanged = { activeP ->
            mediaSession?.setPlayer(activeP)
        }

        // Create launch intent for notification click
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val customCommandToggleFavorite = SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY)
        val customCommandRewind10 = SessionCommand(ACTION_REWIND_10, Bundle.EMPTY)
        val customCommandForward10 = SessionCommand(ACTION_FORWARD_10, Bundle.EMPTY)

        val initialIsLiked = pbManager.currentSong.value?.isFavorite == true

        val rewindButton = CommandButton.Builder()
            .setDisplayName("Rewind 10s")
            .setSessionCommand(customCommandRewind10)
            .setIconResId(R.drawable.ic_replay_10)
            .build()

        val forwardButton = CommandButton.Builder()
            .setDisplayName("Forward 10s")
            .setSessionCommand(customCommandForward10)
            .setIconResId(R.drawable.ic_forward_10)
            .build()

        val initialLikeButton = buildLikeButton(initialIsLiked)

        mediaSession = MediaSession.Builder(this, initialPlayer)
            .setSessionActivity(pendingIntent)
            .setCallback(object : MediaSession.Callback {
                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle
                ): ListenableFuture<SessionResult> {
                    when (customCommand.customAction) {
                        ACTION_TOGGLE_FAVORITE -> {
                            val song = pbManager.currentSong.value
                            if (song != null) {
                                pbManager.toggleFavorite(song)
                            }
                            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                        }
                        ACTION_REWIND_10 -> {
                            pbManager.seekBackward(10_000L)
                            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                        }
                        ACTION_FORWARD_10 -> {
                            pbManager.seekForward(10_000L)
                            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                        }
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
                }
            })
            .setCustomLayout(emptyList())
            .build()

        // Observe current song for like state updates
        serviceScope.launch {
            pbManager.currentSong.collectLatest { song ->
                updateNotificationLayout(song?.isFavorite == true)
            }
        }

        // Use DefaultMediaNotificationProvider
        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(CHANNEL_ID)
            .build()
        setMediaNotificationProvider(notificationProvider)
    }

    private fun buildLikeButton(isLiked: Boolean): CommandButton {
        val customCommandToggleFavorite = SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY)
        return CommandButton.Builder()
            .setDisplayName(if (isLiked) "Unlike" else "Like")
            .setSessionCommand(customCommandToggleFavorite)
            .setIconResId(if (isLiked) R.drawable.ic_favorite else R.drawable.ic_favorite_border)
            .build()
    }

    private fun updateNotificationLayout(isLiked: Boolean) {
        val customCommandRewind10 = SessionCommand(ACTION_REWIND_10, Bundle.EMPTY)
        val customCommandForward10 = SessionCommand(ACTION_FORWARD_10, Bundle.EMPTY)

        val rewindButton = CommandButton.Builder()
            .setDisplayName("Rewind 10s")
            .setSessionCommand(customCommandRewind10)
            .setIconResId(R.drawable.ic_replay_10)
            .build()

        val forwardButton = CommandButton.Builder()
            .setDisplayName("Forward 10s")
            .setSessionCommand(customCommandForward10)
            .setIconResId(R.drawable.ic_forward_10)
            .build()

        val likeButton = buildLikeButton(isLiked)

        // Samsung-style compact notification: prev | play/pause | next only.
        // Empty custom layout lets Media3 use the standard transport controls.
        mediaSession?.setCustomLayout(emptyList())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.notification_channel_name)
            val descriptionText = getString(R.string.notification_channel_description)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
                setSound(null, null)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player != null && (player.playWhenReady || player.mediaItemCount > 0)) {
            return
        }
        stopSelf()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_BACKGROUND || level >= TRIM_MEMORY_RUNNING_LOW) {
            com.example.util.AlbumArtHelper.clearMemoryCache()
            System.gc()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        mediaSession?.run {
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
