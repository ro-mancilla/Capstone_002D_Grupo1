package com.f776.ecopan.feature.settings.unittest

import com.f776.ecopan.domain.preferences.AppTheme
import com.f776.ecopan.feature.settings.screens.settings.SettingsViewModel
import com.f776.ecopan.domain.tenant.Branch
import com.f776.ecopan.feature.settings.unittest.fake.FakeDeviceSessionStore
import com.f776.ecopan.feature.settings.unittest.fake.FakeTenantRepository
import com.f776.ecopan.feature.settings.unittest.fake.FakeReminderPermissionManager
import com.f776.ecopan.feature.settings.unittest.fake.FakeRejectedOperations
import com.f776.ecopan.domain.sync.RejectedOperation
import com.f776.ecopan.domain.sync.SyncEntityType
import kotlin.time.Instant
import com.f776.ecopan.feature.settings.unittest.fake.FakeThemePreferencesDataSource
import com.f776.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private lateinit var viewModel: SettingsViewModel
    private lateinit var themeDataSource: FakeThemePreferencesDataSource
    private lateinit var permissionManager: FakeReminderPermissionManager
    private lateinit var tenantRepository: FakeTenantRepository
    private lateinit var rejectedOperations: FakeRejectedOperations
    private val rejectedSale = RejectedOperation(Uuid.random(), SyncEntityType.SALE, Instant.fromEpochMilliseconds(1_700_000_000_000), 2, "BRANCH_FORBIDDEN")
    private val testDispatcher = MainDispatcherRule.createTestDispatcher()

    @BeforeTest
    fun setUp() {
        MainDispatcherRule.setMainDispatcher(testDispatcher)
        themeDataSource = FakeThemePreferencesDataSource()
        permissionManager = FakeReminderPermissionManager()
        tenantRepository = FakeTenantRepository(
            initialTenant = FakeTenantRepository.profile("Panadería El Sol"),
            initialBranches = listOf(Branch(Uuid.random(), Uuid.random(), "Sucursal Centro", true)),
        )
        rejectedOperations = FakeRejectedOperations(listOf(rejectedSale))
        viewModel = SettingsViewModel(
            themeDataSource,
            permissionManager,
            tenantRepository,
            FakeDeviceSessionStore(deviceId = "baker-07", deviceName = "Caja 1"),
            rejectedOperations,
        )
    }

    @AfterTest
    fun tearDown() {
        MainDispatcherRule.resetMainDispatcher()
    }

    @Test
    fun `initial state is correct`() = runTest {
        val state = viewModel.uiState.first()
        assertEquals(AppTheme.SYSTEM, state.theme)
        assertTrue(state.useDynamicColors)
        assertTrue(state.hasAlarmPermission)
        assertTrue(state.hasNotificationPermission)
    }

    @Test
    fun `setTheme updates state`() = runTest {
        viewModel.setTheme(AppTheme.DARK)
        advanceUntilIdle()
        val state = viewModel.uiState.first()
        assertEquals(AppTheme.DARK, state.theme)
    }

    @Test
    fun `toggleDynamicColors updates state`() = runTest {
        viewModel.toggleDynamicColors(false)
        advanceUntilIdle()
        val state = viewModel.uiState.first()
        assertEquals(false, state.useDynamicColors)
    }

    @Test
    fun `requestAlarmPermission updates permission manager`() = runTest {
        permissionManager.setAlarmPermission(false)
        advanceUntilIdle()
        
        viewModel.requestAlarmPermission()
        advanceUntilIdle()
        
        assertTrue(permissionManager.hasAlarmPermission().first())
    }

    @Test
    fun `requestNotificationPermission updates state`() = runTest {
        permissionManager.setNotificationPermission(false)
        advanceUntilIdle()
        
        var state = viewModel.uiState.first()
        assertEquals(false, state.hasNotificationPermission)
        
        viewModel.requestNotificationPermission()
        advanceUntilIdle()
        
        state = viewModel.uiState.first()
        assertTrue(state.hasNotificationPermission)
    }

    /** The "Acerca de" rows: what support asks a baker to read back. */
    @Test
    fun `installation state names the build, the bakery, the device and its branch as separate values`() = runTest {
        advanceUntilIdle()
        val installation = viewModel.uiState.first().installation

        assertEquals("Panadería El Sol", installation.bakeryName)
        assertEquals("Caja 1", installation.deviceName)
        assertEquals("Sucursal Centro", installation.branchName)
        assertTrue(installation.version.isNotBlank(), "version comes from version.properties via BuildKonfig")
        assertTrue(installation.build.isNotBlank())
    }

    @Test
    fun `the device id never reaches the screen`() = runTest {
        advanceUntilIdle()
        val installation = viewModel.uiState.first().installation

        assertTrue("baker-07" !in installation.toString())
    }

    @Test
    fun `a till enrolled before the name was kept shows its branch and no device name`() = runTest {
        val legacy = SettingsViewModel(themeDataSource, permissionManager, tenantRepository, FakeDeviceSessionStore(deviceId = "baker-07"), rejectedOperations)
        advanceUntilIdle()

        val installation = legacy.uiState.first().installation
        assertEquals("", installation.deviceName)
        assertEquals("Sucursal Centro", installation.branchName)
    }

    @Test
    fun `rejected operations reach the state with the reason the server gave`() = runTest {
        val state = viewModel.uiState.first { it.rejected.isNotEmpty() }

        assertEquals(listOf(rejectedSale), state.rejected)
    }

    @Test
    fun `retrying a rejected operation hands it back to the queue`() = runTest {
        viewModel.retryRejected(rejectedSale.mutationId)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(rejectedSale.mutationId), rejectedOperations.retried)
        assertTrue(viewModel.uiState.first { it.rejected.isEmpty() }.rejected.isEmpty())
    }

    @Test
    fun `discarding a rejected operation removes it from the list and records the discard`() = runTest {
        viewModel.discardRejected(rejectedSale.mutationId)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(rejectedSale.mutationId), rejectedOperations.discarded)
        assertTrue(viewModel.uiState.first { it.rejected.isEmpty() }.rejected.isEmpty())
    }
}
