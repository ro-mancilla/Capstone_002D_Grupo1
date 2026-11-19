package com.f776.ecopan.feature.settings.unittest.fake

import com.f776.ecopan.domain.preferences.AppTheme
import com.f776.ecopan.domain.preferences.ThemePreferencesDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeThemePreferencesDataSource : ThemePreferencesDataSource {
    private val _theme = MutableStateFlow(AppTheme.SYSTEM)
    override val theme: Flow<AppTheme> = _theme.asStateFlow()

    private val _useDynamicColors = MutableStateFlow(true)
    override val useDynamicColors: Flow<Boolean> = _useDynamicColors.asStateFlow()

    override suspend fun setTheme(theme: AppTheme) {
        _theme.value = theme
    }

    override suspend fun setUseDynamicColors(useDynamicColors: Boolean) {
        _useDynamicColors.value = useDynamicColors
    }
}
