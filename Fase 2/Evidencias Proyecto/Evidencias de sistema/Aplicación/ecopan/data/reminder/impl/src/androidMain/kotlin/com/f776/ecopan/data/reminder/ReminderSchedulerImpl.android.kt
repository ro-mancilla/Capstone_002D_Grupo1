@file:OptIn(ExperimentalTime::class)

package com.f776.ecopan.data.reminder

import android.content.Context
import android.util.Log
import com.f776.ecopan.data.reminder.android.alarm.ExactAlarmScheduler
import com.f776.ecopan.data.reminder.android.notification.ReminderBroadcastReceiver
import com.f776.ecopan.domain.reminder.Reminder
import com.f776.ecopan.domain.reminder.ReminderScheduler
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

internal actual class ReminderSchedulerImpl(context: Context) : ReminderScheduler {

    private val alarms = ExactAlarmScheduler(context)

    actual override suspend fun schedule(reminder: Reminder) {
        val triggerAtMillis = reminder.triggerAt.toEpochMilliseconds()
        if (triggerAtMillis <= Clock.System.now().toEpochMilliseconds()) return

        Log.d(TAG, "Scheduling reminder ${reminder.id}")
        alarms.schedule(
            receiver = ReminderBroadcastReceiver::class.java,
            requestCode = requestCode(reminder.id),
            triggerAtMillis = triggerAtMillis
        ) {
            putExtra(REMINDER_ID, reminder.id)
            putExtra(REMINDER_TITLE, reminder.title)
            putExtra(REMINDER_MESSAGE, reminder.message)
        }
    }

    actual override fun cancel(reminderId: String) {
        alarms.cancel(
            receiver = ReminderBroadcastReceiver::class.java,
            requestCode = requestCode(reminderId)
        )
    }

    internal companion object {
        const val REMINDER_ID = "reminder_id"
        const val REMINDER_TITLE = "reminder_title"
        const val REMINDER_MESSAGE = "reminder_message"

        private const val TAG = "ReminderScheduler"

        fun requestCode(reminderId: String): Int = reminderId.hashCode()
    }
}
