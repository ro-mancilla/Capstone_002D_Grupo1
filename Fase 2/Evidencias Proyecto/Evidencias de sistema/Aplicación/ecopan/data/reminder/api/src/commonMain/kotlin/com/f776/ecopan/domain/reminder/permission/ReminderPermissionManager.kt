package com.f776.ecopan.domain.reminder.permission

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

interface ReminderPermissionManager {
    fun hasAlarmPermission(): Flow<Boolean>
    fun hasNotificationPermission(): Flow<Boolean>
    fun isNotificationPermissionRequired(): Boolean

    fun requiresPermissions(): Flow<Boolean> =
        combine(
            hasAlarmPermission(),
            hasNotificationPermission()
        ) { alarm, notification ->
            !alarm || !notification
        }

    fun requestAlarmPermission()
    suspend fun requestNotificationPermission(): PermissionResult

    suspend fun requestPermissions(): PermissionResult {
        val result = requestNotificationPermission()
        if (!hasAlarmPermission().first()) {
            requestAlarmPermission()
        }
        return result
    }
}