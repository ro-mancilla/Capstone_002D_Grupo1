package com.f776.ecopan.domain.preferences

import kotlinx.coroutines.flow.Flow

interface ThemePreferencesDataSource {
    val theme: Flow<AppTheme>
    val useDynamicColors: Flow<Boolean>

    suspend fun setTheme(theme: AppTheme)
    suspend fun setUseDynamicColors(useDynamicColors: Boolean)
}
