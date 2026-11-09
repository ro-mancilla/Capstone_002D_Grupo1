package com.f776.ecopan.data.preferences.unittest

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.f776.ecopan.data.preferences.ThemePreferencesDataSourceImpl
import com.f776.ecopan.data.preferences.unittest.fake.FakePreferencesDataStore
import com.f776.ecopan.domain.preferences.AppTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ThemePreferencesDataSourceImplTest {

    @Test
    fun theme_defaultsToSystem() = runTest {
        val dataSource = ThemePreferencesDataSourceImpl(FakePreferencesDataStore())

        assertEquals(AppTheme.SYSTEM, dataSource.theme.first())
    }

    @Test
    fun useDynamicColors_defaultsToFalse() = runTest {
        val dataSource = ThemePreferencesDataSourceImpl(FakePreferencesDataStore())

        assertEquals(false, dataSource.useDynamicColors.first())
    }

    @Test
    fun setTheme_persistsThemeValue() = runTest {
        val dataSource = ThemePreferencesDataSourceImpl(FakePreferencesDataStore())

        dataSource.setTheme(AppTheme.DARK)

        assertEquals(AppTheme.DARK, dataSource.theme.first())
    }

    @Test
    fun setUseDynamicColors_persistsBooleanValue() = runTest {
        val dataSource = ThemePreferencesDataSourceImpl(FakePreferencesDataStore())

        dataSource.setUseDynamicColors(true)

        assertEquals(true, dataSource.useDynamicColors.first())
    }

    @Test
    fun theme_invalidStoredNameFallsBackToSystem() = runTest {
        val dataStore = FakePreferencesDataStore()
        dataStore.edit {
            it[THEME_KEY] = "BROKEN_THEME"
        }
        val dataSource = ThemePreferencesDataSourceImpl(dataStore)

        assertEquals(AppTheme.SYSTEM, dataSource.theme.first())
    }

    @Test
    fun theme_ioExceptionFallsBackToSystem() = runTest {
        val dataSource = ThemePreferencesDataSourceImpl(
            FakePreferencesDataStore(readError = IOException("disk read failed"))
        )

        assertEquals(AppTheme.SYSTEM, dataSource.theme.first())
    }

    @Test
    fun useDynamicColors_ioExceptionFallsBackToFalse() = runTest {
        val dataSource = ThemePreferencesDataSourceImpl(
            FakePreferencesDataStore(readError = IOException("disk read failed"))
        )

        assertEquals(false, dataSource.useDynamicColors.first())
    }

    @Test
    fun theme_nonIOExceptionIsRethrown() = runTest {
        val dataSource = ThemePreferencesDataSourceImpl(
            FakePreferencesDataStore(readError = IllegalStateException("bad state"))
        )

        assertFailsWith<IllegalStateException> {
            dataSource.theme.first()
        }
    }

    private companion object {
        val THEME_KEY = stringPreferencesKey("theme")
    }
}

