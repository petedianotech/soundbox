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

        // Bind to the single ExoPlayer instance in PlaybackManager
        val pbManager = PlaybackManager.getInstance(this)
        val sharedPlayer = pbManager.player

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
        val initialLikeButton = buildLikeButton(initialIsLiked)

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
            
        mediaSession = MediaSession.Builder(this, sharedPlayer)
            .setSessionActivity(pendingIntent)
            .setCustomLayout(listOf(rewindButton, initialLikeButton, forwardButton))
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(customCommandToggleFavorite)
                        .add(customCommandRewind10)
                        .add(customCommandForward10)
                        .build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(sessionCommands)
                        .build()
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle
                ): ListenableFuture<SessionResult> {
                    when (customCommand.customAction) {
                        ACTION_TOGGLE_FAVORITE -> {
                            val currentSong = pbManager.currentSong.value
                            if (currentSong != null) {
                                pbManager.toggleFavorite(currentSong)
                            }
                        }
                        ACTION_FORWARD_10 -> {
                            val cur = sharedPlayer.currentPosition
                            val dur = sharedPlayer.duration.coerceAtLeast(0L)
                            val target = if (dur > 0) (cur + 10000).coerceAtMost(dur) else cur + 10000
                            sharedPlayer.seekTo(target)
                        }
                        ACTION_REWIND_10 -> {
                            val cur = sharedPlayer.currentPosition
                            val target = (cur - 10000).coerceAtLeast(0L)
                            sharedPlayer.seekTo(target)
                        }
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            })
            .build()
            
        // Observe current song in PlaybackManager to update notification Like button state
        serviceScope.launch {
            pbManager.currentSong.collectLatest { song ->
                updateNotificationLayout(song?.isFavorite == true)
            }
        }

        // Use DefaultMediaNotificationProvider
        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(CHANNEL_ID)
            .setChannelName(R.string.notification_channel_name)
            .build()
        notificationProvider.setSmallIcon(R.drawable.ic_music_note)
        setMediaNotificationProvider(notificationProvider)
    }

    private fun buildLikeButton(isLiked: Boolean): CommandButton {
        val customCommandToggleFavorite = SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY)
        return CommandButton.Builder()
            .setDisplayName(if (isLiked) "Liked" else "Like")
            .setSessionCommand(customCommandToggleFavorite)
            .setIconResId(if (isLiked) R.drawable.ic_thumb_up_filled else R.drawable.ic_thumb_up)
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

        mediaSession?.setCustomLayout(listOf(rewindButton, likeButton, forwardButton))
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

    override fun onDestroy() {
        serviceScope.cancel()
        mediaSession?.run {
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
