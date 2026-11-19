package com.f776.ecopan.data.reminder.android.boot

import com.f776.ecopan.domain.reminder.RemindersProvider
import com.f776.ecopan.domain.reminder.ReminderScheduler

internal class BootScheduler(
    private val remindersProvider: RemindersProvider,
    private val reminderScheduler: ReminderScheduler,
) {
    suspend fun rescheduleAllEnabledReminders() {
        reminderScheduler.rescheduleAll(remindersProvider.currentReminders())
    }
}
