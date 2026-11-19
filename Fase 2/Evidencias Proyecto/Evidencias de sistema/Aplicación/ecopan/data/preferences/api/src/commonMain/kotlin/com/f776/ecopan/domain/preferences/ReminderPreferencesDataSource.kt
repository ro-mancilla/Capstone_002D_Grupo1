package com.f776.ecopan.domain.preferences

import kotlinx.coroutines.flow.Flow

interface ReminderPreferencesDataSource {
    val isReminderEnabled: Flow<Boolean>
    suspend fun setReminderEnabled(enabled: Boolean)
}