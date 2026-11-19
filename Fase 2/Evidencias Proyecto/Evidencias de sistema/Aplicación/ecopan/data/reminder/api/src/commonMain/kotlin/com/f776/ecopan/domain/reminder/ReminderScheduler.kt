package com.f776.ecopan.domain.reminder

interface ReminderScheduler {
    suspend fun schedule(reminder: Reminder)
    fun cancel(reminderId: String)

    /** Re-arms every reminder after a reboot clears the platform's alarm/notification state. */
    suspend fun rescheduleAll(reminders: List<Reminder>) {
        reminders.forEach { schedule(it) }
    }
}
