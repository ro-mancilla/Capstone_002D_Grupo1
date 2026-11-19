@file:OptIn(ExperimentalTime::class)

package com.f776.ecopan.data.reminder

import co.touchlab.kermit.Logger
import com.f776.ecopan.data.reminder.notification.ReminderNotifier
import com.f776.ecopan.domain.reminder.Reminder
import com.f776.ecopan.domain.reminder.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

internal actual class ReminderSchedulerImpl(
    private val notifier: ReminderNotifier,
    private val scope: CoroutineScope
) : ReminderScheduler {
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val scheduledTasks = mutableMapOf<String, ScheduledFuture<*>>()

    actual override suspend fun schedule(reminder: Reminder) {
        scheduledTasks.remove(reminder.id)?.cancel(false)

        val delay = reminder.triggerAt.toEpochMilliseconds() - Clock.System.now().toEpochMilliseconds()
        if (delay <= 0) {
            Logger.e { "Failed to schedule reminder '${reminder.id}': non-positive delay ($delay)" }
            return
        }

        Logger.i { "Reminder '${reminder.title}' in ${delay / 1000}s" }
        scheduledTasks[reminder.id] = scheduler.schedule({
            scope.launch { notifier.notify(reminder.id, reminder.title, reminder.message) }
        }, delay, TimeUnit.MILLISECONDS)
    }

    actual override fun cancel(reminderId: String) {
        scheduledTasks.remove(reminderId)?.cancel(false)
    }
}
