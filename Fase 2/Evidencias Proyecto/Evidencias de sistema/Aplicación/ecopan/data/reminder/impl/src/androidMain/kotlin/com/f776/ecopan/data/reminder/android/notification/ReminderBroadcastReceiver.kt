package com.f776.ecopan.data.reminder.android.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.f776.ecopan.data.reminder.ReminderSchedulerImpl
import com.f776.ecopan.data.reminder.android.doAsync
import com.f776.ecopan.data.reminder.notification.ReminderNotifier
import kotlinx.coroutines.CoroutineScope
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named

internal class ReminderBroadcastReceiver : BroadcastReceiver(), KoinComponent {
    private val notifier by inject<ReminderNotifier>()
    private val coroutineScope by inject<CoroutineScope>(named("defaultCoroutineScope"))

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra(ReminderSchedulerImpl.REMINDER_ID) ?: return
        val title = intent.getStringExtra(ReminderSchedulerImpl.REMINDER_TITLE).orEmpty()
        val message = intent.getStringExtra(ReminderSchedulerImpl.REMINDER_MESSAGE).orEmpty()

        doAsync(coroutineScope) {
            notifier.notify(reminderId, title, message)
        }
    }
}
