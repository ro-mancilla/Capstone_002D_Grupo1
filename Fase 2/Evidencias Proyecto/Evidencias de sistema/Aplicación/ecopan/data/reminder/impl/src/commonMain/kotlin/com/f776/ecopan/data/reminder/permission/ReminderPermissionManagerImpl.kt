package com.f776.ecopan.data.reminder.permission

import com.f776.ecopan.domain.reminder.permission.PermissionResult
import com.f776.ecopan.domain.reminder.permission.ReminderPermissionManager
import kotlinx.coroutines.flow.Flow

internal expect class ReminderPermissionManagerImpl : ReminderPermissionManager {
    override fun hasAlarmPermission(): Flow<Boolean>
    override fun hasNotificationPermission(): Flow<Boolean>
    override fun isNotificationPermissionRequired(): Boolean
    override fun requestAlarmPermission()
    override suspend fun requestNotificationPermission(): PermissionResult
}