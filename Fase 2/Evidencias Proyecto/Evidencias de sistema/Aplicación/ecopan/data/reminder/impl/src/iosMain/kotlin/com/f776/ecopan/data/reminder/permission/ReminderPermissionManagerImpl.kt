@file:OptIn(ExperimentalForeignApi::class)

package com.f776.ecopan.data.reminder.permission

import com.f776.ecopan.domain.reminder.permission.PermissionResult
import com.f776.ecopan.domain.reminder.permission.ReminderPermissionManager
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNNotificationSettings
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

internal actual class ReminderPermissionManagerImpl : ReminderPermissionManager {
    actual override fun hasAlarmPermission(): Flow<Boolean> = flowOf(true)

    actual override fun hasNotificationPermission(): Flow<Boolean> = flow {
        val settings = suspendCancellableCoroutine { continuation ->
            UNUserNotificationCenter.currentNotificationCenter()
                .getNotificationSettingsWithCompletionHandler { settings ->
                    if (continuation.isActive) {
                        continuation.resume(settings)
                    }
                }
        }
        emit(settings?.authorizationStatus == UNAuthorizationStatusAuthorized)
    }

    actual override fun isNotificationPermissionRequired(): Boolean = true

    actual override fun requestAlarmPermission() {
    }

    actual override suspend fun requestNotificationPermission(): PermissionResult {
        return suspendCancellableCoroutine { continuation ->
            UNUserNotificationCenter.currentNotificationCenter()
                .requestAuthorizationWithOptions(
                    options = UNAuthorizationOptionAlert or
                            UNAuthorizationOptionSound or
                            UNAuthorizationOptionBadge,
                    completionHandler = { granted, error ->
                        if (continuation.isCancelled) return@requestAuthorizationWithOptions
                        if (error != null || !granted) {
                            continuation.resume(PermissionResult.DENIED)
                        } else {
                            continuation.resume(PermissionResult.GRANTED)
                        }
                    }
                )
        }
    }
}
