package com.f776.ecopan.unittest

import com.f776.ecopan.AppViewModel
import com.f776.ecopan.unittest.fake.FakeBatchReadyStream
import com.f776.ecopan.unittest.fake.FakeSyncScheduler
import com.f776.ecopan.unittest.fake.FakeTenantRepository
import com.f776.ecopan.unittest.fake.FakeThemePreferencesDataSource
import com.f776.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers the one behaviour `AppViewModel` owns beyond exposing preferences: driving
 * [com.f776.ecopan.domain.sync.SyncScheduler]'s lifecycle off session-ready (03 §7). Before this
 * wiring the scheduler was bound in Koin but never started, so background sync never ran at all.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    private val testDispatcher = MainDispatcherRule.createTestDispatcher()
    private lateinit var scheduler: FakeSyncScheduler
    private lateinit var tenantRepository: FakeTenantRepository
    private lateinit var batchReadyStream: FakeBatchReadyStream

    @BeforeTest
    fun setUp() {
        MainDispatcherRule.setMainDispatcher(testDispatcher)
        scheduler = FakeSyncScheduler()
        tenantRepository = FakeTenantRepository()
        batchReadyStream = FakeBatchReadyStream()
    }

    @AfterTest
    fun tearDown() {
        MainDispatcherRule.resetMainDispatcher()
    }

    private fun viewModel() = AppViewModel(
        themePreferencesDataSource = FakeThemePreferencesDataSource(),
        tenantRepository = tenantRepository,
        syncScheduler = scheduler,
        batchReadyStream = batchReadyStream,
    )

    @Test
    fun `scheduler stays stopped on a fresh install with no device session`() = runTest {
        viewModel()
        advanceUntilIdle()

        assertEquals(0, scheduler.startCalls, "nothing to sync before the device is enrolled")
        assertFalse(scheduler.running)
    }

    @Test
    fun `enrolling starts the scheduler`() = runTest {
        viewModel()
        advanceUntilIdle()

        tenantRepository.enroll()
        advanceUntilIdle()

        assertEquals(1, scheduler.startCalls)
        assertTrue(scheduler.running)
        assertTrue(batchReadyStream.running, "06 §6's live batch-ready stream starts on the same gate")
    }

    @Test
    fun `already-enrolled relaunch starts the scheduler immediately`() = runTest {
        tenantRepository.enroll()

        viewModel()
        advanceUntilIdle()

        assertTrue(scheduler.running, "a returning till must resume syncing without re-enrolling")
    }

    @Test
    fun `clearing the device session stops the scheduler`() = runTest {
        tenantRepository.enroll()
        viewModel()
        advanceUntilIdle()

        tenantRepository.clear()
        advanceUntilIdle()

        assertFalse(scheduler.running)
        assertEquals(1, scheduler.stopCalls)
        assertFalse(batchReadyStream.running, "the SSE subscription must not outlive the device session")
    }

    /** The tenant flow re-emits on every config pull; restarting the loop each time would drop
     *  in-flight work, so the collector is `distinctUntilChanged` on enrolled-ness. */
    @Test
    fun `repeated tenant emissions do not restart the scheduler`() = runTest {
        tenantRepository.enroll()
        viewModel()
        advanceUntilIdle()

        tenantRepository.enroll()
        tenantRepository.enroll()
        advanceUntilIdle()

        assertEquals(1, scheduler.startCalls)
        assertEquals(1, batchReadyStream.startCalls, "and no second SSE connection either")
    }
}
