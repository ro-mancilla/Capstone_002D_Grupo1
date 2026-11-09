@file:OptIn(ExperimentalForeignApi::class)

package com.f776.ecopan.data.reminder

import com.f776.ecopan.domain.reminder.Reminder
import com.f776.ecopan.domain.reminder.ReminderScheduler
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import platform.Foundation.NSCalendar
import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

internal actual class ReminderSchedulerImpl : ReminderScheduler {

    actual override suspend fun schedule(reminder: Reminder) {
        val content = UNMutableNotificationContent()
        content.setTitle(reminder.title)
        content.setBody(reminder.message)
        content.setSound(UNNotificationSound.defaultSound)

        val local = reminder.triggerAt.toLocalDateTime(TimeZone.currentSystemDefault())
        val dateComponents = NSDateComponents().apply {
            calendar = NSCalendar.currentCalendar
            year = local.year.toLong()
            // NSDateComponents months are 1-based, matching Month's declaration order.
            month = (local.month.ordinal + 1).toLong()
            day = local.day.toLong()
            hour = local.hour.toLong()
            minute = local.minute.toLong()
        }

        val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(
            dateComponents = dateComponents,
            repeats = false
        )

        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = reminder.id,
            content = content,
            trigger = trigger
        )

        UNUserNotificationCenter.currentNotificationCenter()
            .addNotificationRequest(request, withCompletionHandler = null)
    }

    actual override fun cancel(reminderId: String) {
        UNUserNotificationCenter.currentNotificationCenter()
            .removePendingNotificationRequestsWithIdentifiers(listOf(reminderId))
    }
}
