package com.f776.ecopan.domain.reminder

/**
 * Supplies every reminder that should currently be armed, so [ReminderScheduler] can re-arm them
 * after a reboot clears the platform's alarms. Bind your app's real implementation over the
 * default no-op one in [com.f776.ecopan.data.reminder.di.ReminderDataModule].
 */
fun interface RemindersProvider {
    suspend fun currentReminders(): List<Reminder>
}
