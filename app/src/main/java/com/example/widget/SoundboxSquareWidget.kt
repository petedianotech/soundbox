package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.player.PlaybackManager
import com.example.util.AlbumArtHelper

class SoundboxSquareWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        if (action != null) {
            val playbackManager = PlaybackManager.getInstance(context.applicationContext)
            when (action) {
                ACTION_PLAY_PAUSE -> playbackManager.playPause()
                ACTION_NEXT -> playbackManager.skipNext()
                ACTION_PREV -> playbackManager.skipPrevious()
                ACTION_WIDGET_UPDATE -> {
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val thisWidget = ComponentName(context, SoundboxSquareWidget::class.java)
                    val ids = appWidgetManager.getAppWidgetIds(thisWidget)
                    onUpdate(context, appWidgetManager, ids)
                }
            }
            // Trigger UI update across all instances
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, SoundboxSquareWidget::class.java)
            val ids = appWidgetManager.getAppWidgetIds(thisWidget)
            for (id in ids) {
                updateAppWidget(context, appWidgetManager, id)
            }
        }
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.example.ACTION_SQUARE_PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.ACTION_SQUARE_NEXT"
        const val ACTION_PREV = "com.example.ACTION_SQUARE_PREV"
        const val ACTION_WIDGET_UPDATE = "com.example.ACTION_SQUARE_WIDGET_UPDATE"

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.soundbox_square_widget)
            val playbackManager = PlaybackManager.getInstance(context.applicationContext)
            val currentSong = playbackManager.currentSong.value
            val isPlaying = playbackManager.isPlaying.value

            if (currentSong != null) {
                views.setTextViewText(R.id.widget_square_title, currentSong.title)
                views.setTextViewText(R.id.widget_square_artist, currentSong.artist)

                val bitmap = AlbumArtHelper.getWidgetArtworkBitmap(context, currentSong, 240)
                if (bitmap != null) {
                    views.setImageViewBitmap(R.id.widget_square_album_art, bitmap)
                } else {
                    views.setImageViewResource(R.id.widget_square_album_art, R.drawable.ic_music_note)
                }
            } else {
                views.setTextViewText(R.id.widget_square_title, "Soundbox Audio")
                views.setTextViewText(R.id.widget_square_artist, "Tap play to start")
                views.setImageViewResource(R.id.widget_square_album_art, R.drawable.ic_music_note)
            }

            views.setImageViewResource(
                R.id.widget_square_btn_play_pause,
                if (isPlaying) R.drawable.ic_pause_widget else R.drawable.ic_play_widget
            )

            // Pending Intents for Controls
            views.setOnClickPendingIntent(R.id.widget_square_btn_play_pause, getPendingIntent(context, ACTION_PLAY_PAUSE))
            views.setOnClickPendingIntent(R.id.widget_square_btn_next, getPendingIntent(context, ACTION_NEXT))
            views.setOnClickPendingIntent(R.id.widget_square_btn_prev, getPendingIntent(context, ACTION_PREV))

            // Open Main App on clicking container or album art
            val mainIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val mainPendingIntent = PendingIntent.getActivity(
                context, 101, mainIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_square_container, mainPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_square_album_art, mainPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun getPendingIntent(context: Context, action: String): PendingIntent {
            val intent = Intent(context, SoundboxSquareWidget::class.java).apply {
                this.action = action
            }
            return PendingIntent.getBroadcast(
                context, action.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        fun updateAllWidgets(context: Context) {
            val intent = Intent(context, SoundboxSquareWidget::class.java).apply {
                action = ACTION_WIDGET_UPDATE
            }
            context.sendBroadcast(intent)
        }
    }
}
