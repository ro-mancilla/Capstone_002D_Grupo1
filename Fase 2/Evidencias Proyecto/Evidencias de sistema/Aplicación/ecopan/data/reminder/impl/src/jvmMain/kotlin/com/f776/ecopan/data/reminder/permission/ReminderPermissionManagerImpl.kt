package com.f776.ecopan.data.reminder.permission

import com.f776.ecopan.domain.reminder.permission.PermissionResult
import com.f776.ecopan.domain.reminder.permission.ReminderPermissionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal actual class ReminderPermissionManagerImpl : ReminderPermissionManager {
    actual override fun hasAlarmPermission(): Flow<Boolean> = flowOf(true)

    actual override fun hasNotificationPermission(): Flow<Boolean> = flowOf(true)

    actual override fun isNotificationPermissionRequired(): Boolean = false

    actual override fun requestAlarmPermission() {
        // No-op: No runtime permission needed on Desktop
    }

    actual override suspend fun requestNotificationPermission(): PermissionResult {
        return PermissionResult.GRANTED
    }
}