package com.f776.ecopan.feature.home.unittest.fake

import com.f776.ecopan.domain.reminder.Reminder
import com.f776.ecopan.domain.reminder.ReminderScheduler

class FakeReminderScheduler : ReminderScheduler {
    val scheduleCalls = mutableListOf<Reminder>()
    val cancelCalls = mutableListOf<String>()

    override suspend fun schedule(reminder: Reminder) {
        scheduleCalls += reminder
    }

    override fun cancel(reminderId: String) {
        cancelCalls += reminderId
    }
}
