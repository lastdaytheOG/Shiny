package com.shiny.music.recognition

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.shiny.music.MainActivity
import com.shiny.music.R
import com.music.shazamkit.models.RecognitionResult
import timber.log.Timber

object ShazamNotificationHelper {
    const val RESULT_CHANNEL_ID = "song_recognition_results_v2"
    const val RESULT_NOTIFICATION_ID = 9102

    fun createResultChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                RESULT_CHANNEL_ID,
                "Song Recognition Results",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Apple Shazam style notifications when a song is recognized"
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    fun postSongFoundNotification(
        context: Context,
        result: RecognitionResult,
        coverBitmap: Bitmap? = null
    ) {
        try {
            createResultChannel(context)

            val playIntent = Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_RECOGNITION
                putExtra(RecognitionForegroundService.EXTRA_RECOGNITION_TRACK_ID, result.trackId)
                putExtra(RecognitionForegroundService.EXTRA_RECOGNITION_TITLE, result.title)
                putExtra(RecognitionForegroundService.EXTRA_RECOGNITION_ARTIST, result.artist)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

            val playPendingIntent = PendingIntent.getActivity(
                context,
                (result.trackId + "_play").hashCode(),
                playIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val historyIntent = Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_RECOGNITION
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

            val historyPendingIntent = PendingIntent.getActivity(
                context,
                (result.trackId + "_history").hashCode(),
                historyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val title = "🎵 ${result.title}"
            val artistAndAlbum = if (!result.album.isNullOrBlank()) "${result.artist} • ${result.album}" else result.artist

            val builder = NotificationCompat.Builder(context, RESULT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_widget_mic)
                .setSubText(" Shazam • Match Found")
                .setContentTitle(title)
                .setContentText(artistAndAlbum)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(playPendingIntent)
                .setVibrate(longArrayOf(0, 250, 100, 250))
                .addAction(
                    0,
                    "▶ Listen on Shiny",
                    playPendingIntent
                )
                .addAction(
                    0,
                    "📜 View History",
                    historyPendingIntent
                )

            if (coverBitmap != null) {
                builder.setLargeIcon(coverBitmap)
                builder.setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(coverBitmap)
                        .bigLargeIcon(null as Bitmap?)
                        .setSummaryText(artistAndAlbum)
                )
            } else {
                builder.setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("$artistAndAlbum\nTap to listen on Shiny.")
                )
            }

            NotificationManagerCompat.from(context).notify(RESULT_NOTIFICATION_ID, builder.build())
        } catch (e: Exception) {
            Timber.tag("ShazamNotif").w(e, "Failed to post song found notification")
        }
    }
}
