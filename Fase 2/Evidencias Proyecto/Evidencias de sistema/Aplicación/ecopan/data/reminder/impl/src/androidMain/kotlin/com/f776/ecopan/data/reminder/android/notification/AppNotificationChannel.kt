package com.f776.ecopan.data.reminder.android.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.f776.ecopan.data.reminder.impl.R

class AppNotificationChannel(private val context: Context) {
    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                /* id = */ CHANNEL_ID,
                /* name = */ context.getString(R.string.channel_name),
                /* importance = */ NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_description)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                    as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    internal companion object {
        internal const val CHANNEL_ID = "class_reminders"
    }
}

