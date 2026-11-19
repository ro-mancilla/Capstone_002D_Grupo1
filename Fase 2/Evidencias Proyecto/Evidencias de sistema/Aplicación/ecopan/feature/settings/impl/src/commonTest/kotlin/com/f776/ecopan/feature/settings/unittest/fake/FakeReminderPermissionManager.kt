package com.f776.ecopan.feature.settings.unittest.fake

import com.f776.ecopan.domain.reminder.permission.PermissionResult
import com.f776.ecopan.domain.reminder.permission.ReminderPermissionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeReminderPermissionManager : ReminderPermissionManager {
    private val _hasAlarmPermission = MutableStateFlow(true)
    override fun hasAlarmPermission(): Flow<Boolean> = _hasAlarmPermission.asStateFlow()

    private val _hasNotificationPermission = MutableStateFlow(true)
    override fun hasNotificationPermission(): Flow<Boolean> = _hasNotificationPermission.asStateFlow()

    override fun isNotificationPermissionRequired(): Boolean = false

    override fun requestAlarmPermission() {
        _hasAlarmPermission.value = true
    }

    override suspend fun requestNotificationPermission(): PermissionResult {
        _hasNotificationPermission.value = true
        return PermissionResult.GRANTED
    }

    fun setAlarmPermission(granted: Boolean) {
        _hasAlarmPermission.value = granted
    }

    fun setNotificationPermission(granted: Boolean) {
        _hasNotificationPermission.value = granted
    }
}
