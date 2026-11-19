package com.f776.ecopan.data.reminder.notification

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class ReminderNotifier(private val ioDispatcher: CoroutineDispatcher) {

    suspend fun notify(reminderId: String, title: String, message: String) =
        withContext(ioDispatcher) {
            Logger.i { "Notifying reminder $reminderId: $title" }
            DesktopNotifications.show(title = title, message = message)
            Unit
        }
}
