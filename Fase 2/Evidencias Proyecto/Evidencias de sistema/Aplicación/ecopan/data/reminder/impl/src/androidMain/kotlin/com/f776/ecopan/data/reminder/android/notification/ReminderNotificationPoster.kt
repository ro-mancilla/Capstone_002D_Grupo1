package com.f776.ecopan.data.reminder.android.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.f776.ecopan.data.reminder.impl.R

/**
 * Builds and posts reminder notifications.
 *
 * Both reminder kinds want the same notification: the app icon, high priority, auto-cancel, and a
 * tap that deep links into the app. Only the text, the id and the link differ, so those are the
 * only things callers supply.
 */
internal class ReminderNotificationPoster(private val context: Context) {

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun post(id: Int, title: String, message: String, deepLink: String) {
        val notification = NotificationCompat.Builder(context, AppNotificationChannel.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(id, deepLink))
            .build()

        notificationManager.notify(id, notification)
    }

    /**
     * The request code is the notification id so that two pending notifications of different
     * items keep separate intents; sharing code 0 made a second notification overwrite the first
     * one's destination.
     */
    private fun contentIntent(id: Int, deepLink: String): PendingIntent {
        val packageName = context.packageName
        val intent = Intent(Intent.ACTION_VIEW, deepLink.toUri()).apply {
            setClassName(packageName, "$packageName.MainActivity")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        return PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
