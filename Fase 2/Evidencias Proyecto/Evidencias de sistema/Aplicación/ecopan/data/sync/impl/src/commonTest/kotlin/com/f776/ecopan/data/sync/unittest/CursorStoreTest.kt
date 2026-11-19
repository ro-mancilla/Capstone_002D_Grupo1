package com.f776.ecopan.data.sync.unittest

import com.f776.ecopan.data.sync.CursorStore
import com.f776.ecopan.data.sync.unittest.fake.FakePreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class CursorStoreTest {

    @Test
    fun `defaults to zero with nothing stored yet`() = runTest {
        val store = CursorStore(FakePreferencesDataStore())

        assertEquals(0L, store.observe().first())
    }

    @Test
    fun `advanceTo persists and is observable`() = runTest {
        val store = CursorStore(FakePreferencesDataStore())

        store.advanceTo(48211L)

        assertEquals(48211L, store.observe().first())
    }

    @Test
    fun `reset returns the cursor to zero as on re-bootstrap`() = runTest {
        val store = CursorStore(FakePreferencesDataStore())
        store.advanceTo(48211L)

        store.reset()

        assertEquals(0L, store.observe().first())
    }

    @Test
    fun `an IOException reading the store falls back to zero rather than crashing`() = runTest {
        val store = CursorStore(FakePreferencesDataStore(readError = IOException("disk read failed")))

        assertEquals(0L, store.observe().first())
    }

    @Test
    fun `last pull time is unknown until one completes, then persists across a reset`() = runTest {
        val store = CursorStore(FakePreferencesDataStore())
        assertNull(store.observeLastPulledAt().first())

        val at = Instant.parse("2026-10-07T04:50:00Z")
        store.recordPulledAt(at)
        store.reset()

        assertEquals(at, store.observeLastPulledAt().first())
    }
}
