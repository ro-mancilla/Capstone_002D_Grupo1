package com.f776.ecopan.feature.shell.unittest

import com.f776.core.designsystem.component.SyncIndicatorState
import com.f776.ecopan.domain.sync.ConnectivityStatus
import com.f776.ecopan.domain.sync.SyncError
import com.f776.ecopan.domain.sync.SyncStatus
import com.f776.ecopan.feature.shell.ShellContextImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * The invariants three ViewModels used to restate. The branch rule in particular existed only as
 * a prose comment before this.
 */
class ShellContextImplTest {

    private val tenantRepository = FakeTenantRepository()
    private val sessionProvider = FakeSessionProvider()
    private val syncEngine = FakeSyncEngine()
    private val shell = ShellContextImpl(tenantRepository, sessionProvider, syncEngine)

    @Test
    fun `no branches means no active branch rather than a crash`() = runTest {
        assertNull(shell.shift.first().branchId)
    }

    @Test
    fun `the active branch is the first one the device cached`() = runTest {
        val first = Uuid.random()
        tenantRepository.setBranches(first, Uuid.random())

        assertEquals(first, shell.shift.first().branchId)
    }

    @Test
    fun `the shift carries the signed-in user and the bakery's name`() = runTest {
        val userId = Uuid.random()
        sessionProvider.setUser(userId)
        tenantRepository.setTenantName("Panadería Reference")

        val shift = shell.shift.first()
        assertEquals(userId, shift.userId)
        assertEquals("Panadería Reference", shift.tenantName)
    }

    @Test
    fun `a bakery with no cached profile shows an empty name and not null`() = runTest {
        assertEquals("", shell.shift.first().tenantName)
    }

    @Test
    fun `every sync status maps to an indicator and both failure kinds need attention`() = runTest {
        val cases = listOf(
            SyncStatus.Idle(ConnectivityStatus.ONLINE) to SyncIndicatorState.OnlineIdle,
            SyncStatus.Idle(ConnectivityStatus.OFFLINE) to SyncIndicatorState.OfflinePending(0),
            SyncStatus.Syncing(ConnectivityStatus.ONLINE) to SyncIndicatorState.Syncing,
            SyncStatus.Pending(3, ConnectivityStatus.OFFLINE) to SyncIndicatorState.OfflinePending(3),
            SyncStatus.AuthRequired(2, ConnectivityStatus.ONLINE) to SyncIndicatorState.Attention(2),
            SyncStatus.Error(5, SyncError("offline", retriable = true), ConnectivityStatus.ONLINE) to SyncIndicatorState.Attention(5),
        )

        for ((status, expected) in cases) {
            syncEngine.emit(status)
            assertEquals(expected, shell.indicator.first(), "$status")
        }
    }

    @Test
    fun `syncNow asks the engine to sync`() = runTest {
        shell.syncNow()

        assertTrue(syncEngine.syncNowCalls == 1)
    }

    @Test
    fun `the last sync time is the sync engine's, unknown until one completes`() = runTest {
        assertNull(shell.lastSyncedAt.first())

        val at = kotlin.time.Instant.parse("2026-10-07T04:50:00Z")
        syncEngine.lastSynced.value = at

        assertEquals(at, shell.lastSyncedAt.first())
    }
}
