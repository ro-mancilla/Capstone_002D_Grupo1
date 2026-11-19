package com.f776.ecopan.data.reminder

import com.f776.ecopan.domain.reminder.Reminder
import com.f776.ecopan.domain.reminder.ReminderScheduler

internal expect class ReminderSchedulerImpl : ReminderScheduler {
    override suspend fun schedule(reminder: Reminder)
    override fun cancel(reminderId: String)
}
