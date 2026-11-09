package com.f776.ecopan.data.tenant.unittest

import com.f776.ecopan.data.tenant.TokenStore
import com.f776.ecopan.data.tenant.unittest.fake.FakePreferencesDataStore
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class TokenStoreTest {

    @Test
    fun `no tokens cached yet reads as null`() = runTest {
        val store = TokenStore(FakePreferencesDataStore())

        assertNull(store.currentAccessToken())
        assertNull(store.currentRefreshToken())
    }

    @Test
    fun `saveTokens persists and is readable back`() = runTest {
        val store = TokenStore(FakePreferencesDataStore())

        store.saveTokens("access-1", "refresh-1")

        assertEquals("access-1", store.currentAccessToken())
        assertEquals("refresh-1", store.currentRefreshToken())
    }

    @Test
    fun `saveTokens overwrites a previous pair as on refresh`() = runTest {
        val store = TokenStore(FakePreferencesDataStore())
        store.saveTokens("access-1", "refresh-1")

        store.saveTokens("access-2", "refresh-2")

        assertEquals("access-2", store.currentAccessToken())
        assertEquals("refresh-2", store.currentRefreshToken())
    }

    @Test
    fun `clear wipes both tokens`() = runTest {
        val store = TokenStore(FakePreferencesDataStore())
        store.saveTokens("access-1", "refresh-1")

        store.clear()

        assertNull(store.currentAccessToken())
        assertNull(store.currentRefreshToken())
    }

    @Test
    fun `deviceId is generated once and persists across calls`() = runTest {
        val store = TokenStore(FakePreferencesDataStore())

        val first = store.deviceId()
        val second = store.deviceId()

        assertEquals(first, second)
    }

    @Test
    fun `deviceId differs across independently-backed stores`() = runTest {
        val storeA = TokenStore(FakePreferencesDataStore())
        val storeB = TokenStore(FakePreferencesDataStore())

        assertNotEquals(storeA.deviceId(), storeB.deviceId())
    }

    @Test
    fun `an IOException reading the store falls back to empty rather than crashing`() = runTest {
        val store = TokenStore(FakePreferencesDataStore(readError = IOException("disk read failed")))

        assertNull(store.currentAccessToken())
        assertNull(store.currentRefreshToken())
    }
}
