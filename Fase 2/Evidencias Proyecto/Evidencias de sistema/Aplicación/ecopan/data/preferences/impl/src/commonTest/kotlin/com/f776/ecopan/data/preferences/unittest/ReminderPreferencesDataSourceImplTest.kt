package com.f776.ecopan.data.preferences.unittest

import com.f776.ecopan.data.preferences.ReminderPreferencesDataSourceImpl
import com.f776.ecopan.data.preferences.unittest.fake.FakePreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReminderPreferencesDataSourceImplTest {

    @Test
    fun reminderEnabled_defaultsToTrue() = runTest {
        val dataSource = ReminderPreferencesDataSourceImpl(FakePreferencesDataStore())

        assertEquals(true, dataSource.isReminderEnabled.first())
    }

    @Test
    fun setReminderEnabled_updatesStoredValue() = runTest {
        val dataSource = ReminderPreferencesDataSourceImpl(FakePreferencesDataStore())

        dataSource.setReminderEnabled(false)

        assertEquals(false, dataSource.isReminderEnabled.first())
    }

    @Test
    fun reminderEnabled_ioExceptionFallsBackToDefaultTrue() = runTest {
        val dataSource = ReminderPreferencesDataSourceImpl(
            FakePreferencesDataStore(readError = IOException("disk read failed"))
        )

        assertEquals(true, dataSource.isReminderEnabled.first())
    }

    @Test
    fun reminderEnabled_nonIOExceptionIsRethrown() = runTest {
        val dataSource = ReminderPreferencesDataSourceImpl(
            FakePreferencesDataStore(readError = IllegalArgumentException("bad state"))
        )

        assertFailsWith<IllegalArgumentException> {
            dataSource.isReminderEnabled.first()
        }
    }
}

