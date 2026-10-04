package com.shiny.music.together

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.shiny.music.MainActivity
import com.shiny.music.R
import com.shiny.music.constants.TogetherRequestAlertsKey
import com.shiny.music.utils.dataStore
import com.shiny.music.utils.get
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * "Ana wants to join" in the shade, with Let In and Decline right there — a host with the
 * phone in a pocket shouldn't have to open the app to let a friend in.
 */
class TogetherNotifications(private val context: Context) {
    private val shown = HashMap<String, Int>()

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(Channel) != null) return
        manager.createNotificationChannel(
            NotificationChannel(Channel, context.getString(R.string.together_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.together_channel_desc)
            }
        )
    }

    fun showRequest(id: String, name: String) {
        if (!context.dataStore.get(TogetherRequestAlertsKey, true)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel()
        val notificationId = shown.getOrPut(id) { BaseId + (id.hashCode() and 0xFFFF) }
        val open = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .setData(android.net.Uri.parse("shinymusic://together"))
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, Channel)
            .setSmallIcon(R.drawable.group)
            .setContentTitle(context.getString(R.string.together_request_title, name))
            .setContentText(context.getString(R.string.together_request_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.together_let_in), action(ActionApprove, id, notificationId))
            .addAction(0, context.getString(R.string.together_decline), action(ActionDecline, id, notificationId))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(notificationId, notification) }
    }

    fun cancelRequest(id: String) {
        val notificationId = shown.remove(id) ?: return
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    fun cancelAll() {
        shown.values.forEach { NotificationManagerCompat.from(context).cancel(it) }
        shown.clear()
    }

    private fun action(action: String, id: String, notificationId: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            notificationId * 2 + if (action == ActionApprove) 0 else 1,
            Intent(context, TogetherActionReceiver::class.java)
                .setAction(action)
                .putExtra(ExtraRequest, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        const val Channel = "together"
        const val ActionApprove = "com.shiny.music.together.APPROVE"
        const val ActionDecline = "com.shiny.music.together.DECLINE"
        const val ExtraRequest = "request"
        private const val BaseId = 0x7A000
    }
}

@AndroidEntryPoint
class TogetherActionReceiver : BroadcastReceiver() {
    @Inject
    lateinit var session: TogetherSession

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(TogetherNotifications.ExtraRequest) ?: return
        when (intent.action) {
            TogetherNotifications.ActionApprove -> session.approve(id)
            TogetherNotifications.ActionDecline -> session.deny(id)
        }
    }
}
