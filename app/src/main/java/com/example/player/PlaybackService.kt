package com.example.player

import android.content.Intent
import android.os.Bundle
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.Futures
import kotlinx.coroutines.launch
import com.example.R

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        // Bind to the single ExoPlayer instance in PlaybackManager
        val sharedPlayer = PlaybackManager.getInstance(this).player
        
        val customCommandToggleFavorite = SessionCommand("ACTION_TOGGLE_FAVORITE", Bundle.EMPTY)
        val toggleFavoriteButton = CommandButton.Builder()
            .setDisplayName("Toggle Favorite")
            .setSessionCommand(customCommandToggleFavorite)
            .setIconResId(R.drawable.ic_heart) 
            .build()
            
        mediaSession = MediaSession.Builder(this, sharedPlayer)
            .setCustomLayout(listOf(toggleFavoriteButton))
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(customCommandToggleFavorite)
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
                    if (customCommand.customAction == "ACTION_TOGGLE_FAVORITE") {
                        val pbManager = PlaybackManager.getInstance(this@PlaybackService)
                        val song = pbManager.currentSong.value
                        if (song != null) {
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                com.example.data.repository.MusicRepository.getInstance(this@PlaybackService)
                                    .toggleFavorite(song.id, song.isFavorite)
                            }
                        }
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            })
            .build()
            
        // Use DefaultMediaNotificationProvider which implements MediaStyle notification under the hood
        val notificationProvider = DefaultMediaNotificationProvider.Builder(this).build()
        // Improve small icon visibility by using a simple recognizable shape (avoid full color PNGs for small icons)
        notificationProvider.setSmallIcon(R.drawable.ic_notification_large)
        setMediaNotificationProvider(notificationProvider)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        // Don't stop service if playing. If not, stop self.
        if (player != null && !player.playWhenReady) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
