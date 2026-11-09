package com.f776.ecopan.data.reminder.notification

import com.f776.ecopan.data.reminder.android.notification.ReminderNotificationPoster
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class ReminderNotifier(
    private val poster: ReminderNotificationPoster,
    private val ioDispatcher: CoroutineDispatcher
) {
    suspend fun notify(reminderId: String, title: String, message: String) =
        withContext(ioDispatcher) {
            poster.post(
                id = reminderId.hashCode(),
                title = title,
                message = message,
                deepLink = "ecopan://example/$reminderId"
            )
        }
}
