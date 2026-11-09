@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalUuidApi::class)

package com.f776.ecopan.feature.auth.unittest

import com.f776.ecopan.core.domain.auth.Role
import com.f776.ecopan.domain.tenant.LoginOutcome
import com.f776.ecopan.domain.tenant.UserAccount
import com.f776.ecopan.feature.auth.screens.auth.AuthAction
import com.f776.ecopan.feature.auth.screens.auth.AuthPhase
import com.f776.ecopan.feature.auth.screens.auth.AuthViewModel
import com.f776.ecopan.feature.auth.screens.auth.EnrollError
import com.f776.ecopan.feature.auth.unittest.fake.FakeAuthRepository
import com.f776.ecopan.feature.auth.unittest.fake.FakeSessionProvider
import com.f776.ecopan.feature.auth.unittest.fake.FakeSyncEngine
import com.f776.ecopan.feature.auth.unittest.fake.FakeTenantRepository
import com.f776.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class AuthViewModelTest {

    private lateinit var tenantRepository: FakeTenantRepository
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var syncEngine: FakeSyncEngine
    private lateinit var sessionProvider: FakeSessionProvider
    private lateinit var viewModel: AuthViewModel
    private val testDispatcher = MainDispatcherRule.createTestDispatcher()

    @BeforeTest
    fun setUp() {
        MainDispatcherRule.setMainDispatcher(testDispatcher)
        tenantRepository = FakeTenantRepository()
        sessionProvider = FakeSessionProvider()
        authRepository = FakeAuthRepository(tenantRepository, sessionProvider)
        syncEngine = FakeSyncEngine()
        viewModel = AuthViewModel(tenantRepository, authRepository, syncEngine, sessionProvider, pinUnlockSupported = true, devQuickLoginEnabled = true)
    }

    @AfterTest
    fun tearDown() {
        MainDispatcherRule.resetMainDispatcher()
    }

    @Test
    fun `no tenant cached yet starts in enrollment`() = runTest {
        assertEquals(AuthPhase.ENROLLMENT, viewModel.uiState.first { !it.loading }.phase)
    }

    @Test
    fun `successful login populates tenant and staff triggers bootstrap and reaches unlocked`() = runTest {
        viewModel.uiState.first { !it.loading }

        viewModel.onAction(AuthAction.OnTenantSlugChanged("la-espiga"))
        viewModel.onAction(AuthAction.OnUsernameChanged("cami"))
        viewModel.onAction(AuthAction.OnPasswordChanged("secret"))
        viewModel.onAction(AuthAction.OnEnrollTapped)
        advanceUntilIdle()

        val state = viewModel.uiState.first()
        assertEquals(AuthPhase.UNLOCKED, state.phase)
        assertEquals("Panadería la-espiga", tenantRepository.tenant.value?.name)
        assertTrue(tenantRepository.users.value.isNotEmpty())
        assertEquals(1, authRepository.loginCallCount)
        assertEquals(1, syncEngine.bootstrapCallCount)
        assertNull(state.enrollError)
    }

    @Test
    fun `invalid credentials shows an error and stays in enrollment`() = runTest {
        viewModel.uiState.first { !it.loading }
        authRepository.resultOverride = LoginOutcome.InvalidCredentials

        viewModel.onAction(AuthAction.OnTenantSlugChanged("la-espiga"))
        viewModel.onAction(AuthAction.OnEnrollTapped)
        advanceUntilIdle()

        val state = viewModel.uiState.first()
        assertEquals(AuthPhase.ENROLLMENT, state.phase)
        assertEquals(EnrollError.INVALID_CREDENTIALS, state.enrollError)
        assertEquals(0, syncEngine.bootstrapCallCount)
        assertEquals(null, tenantRepository.tenant.value)
    }

    @Test
    fun `a throttled sign-in says to wait, and is neither a wrong password nor offline`() = runTest {
        viewModel.uiState.first { !it.loading }
        authRepository.resultOverride = LoginOutcome.TooManyAttempts

        viewModel.onAction(AuthAction.OnTenantSlugChanged("la-espiga"))
        viewModel.onAction(AuthAction.OnEnrollTapped)
        advanceUntilIdle()

        val state = viewModel.uiState.first()
        assertEquals(AuthPhase.ENROLLMENT, state.phase)
        assertEquals(EnrollError.TOO_MANY_ATTEMPTS, state.enrollError)
    }

    @Test
    fun `network failure on fresh install shows the needs-connection state`() = runTest {
        viewModel.uiState.first { !it.loading }
        authRepository.resultOverride = LoginOutcome.NetworkError

        viewModel.onAction(AuthAction.OnTenantSlugChanged("la-espiga"))
        viewModel.onAction(AuthAction.OnEnrollTapped)
        advanceUntilIdle()

        val state = viewModel.uiState.first()
        assertEquals(AuthPhase.ENROLLMENT, state.phase)
        assertEquals(EnrollError.NEEDS_CONNECTION, state.enrollError)
        assertEquals(0, syncEngine.bootstrapCallCount)
        assertEquals(null, tenantRepository.tenant.value)
    }

    @Test
    fun `an existing session skips straight past lock`() = runTest {
        val tenantId = Uuid.random()
        tenantRepository.tenant.value = com.f776.ecopan.domain.tenant.TenantProfile(tenantId, "s", "S", "CLP", emptySet())
        val userId = Uuid.random()
        sessionProvider.currentUserIdState.value = userId

        assertEquals(AuthPhase.UNLOCKED, viewModel.uiState.first { !it.loading }.phase)
    }

    @Test
    fun `correct pin unlocks the selected staff member`() = runTest {
        val tenantId = Uuid.random()
        val userId = Uuid.random()
        tenantRepository.tenant.value = com.f776.ecopan.domain.tenant.TenantProfile(tenantId, "s", "S", "CLP", emptySet())
        tenantRepository.users.value = listOf(UserAccount(userId, tenantId, null, "cami", "Camila Reyes", Role.CASHIER, true, hasPin = true))
        tenantRepository.pinHashesByUserId[userId] = "1234"
        viewModel.uiState.first { !it.loading }

        viewModel.onAction(AuthAction.OnStaffSelected(userId))
        "1234".forEach { viewModel.onAction(AuthAction.OnPinDigitTapped(it.toString())) }
        advanceUntilIdle()

        assertEquals(userId, sessionProvider.currentUserIdState.value)
        assertEquals(AuthPhase.UNLOCKED, viewModel.uiState.first().phase)
    }

    @Test
    fun `wrong pin shows an error and does not unlock`() = runTest {
        val tenantId = Uuid.random()
        val userId = Uuid.random()
        tenantRepository.tenant.value = com.f776.ecopan.domain.tenant.TenantProfile(tenantId, "s", "S", "CLP", emptySet())
        tenantRepository.users.value = listOf(UserAccount(userId, tenantId, null, "cami", "Camila Reyes", Role.CASHIER, true, hasPin = true))
        tenantRepository.pinHashesByUserId[userId] = "1234"
        viewModel.uiState.first { !it.loading }

        viewModel.onAction(AuthAction.OnStaffSelected(userId))
        "0000".forEach { viewModel.onAction(AuthAction.OnPinDigitTapped(it.toString())) }
        advanceUntilIdle()

        val state = viewModel.uiState.first()
        assertTrue(state.pinError)
        assertEquals(null, sessionProvider.currentUserIdState.value)
    }

    @Test
    fun `five wrong pins lock out further attempts`() = runTest {
        val tenantId = Uuid.random()
        val userId = Uuid.random()
        tenantRepository.tenant.value = com.f776.ecopan.domain.tenant.TenantProfile(tenantId, "s", "S", "CLP", emptySet())
        tenantRepository.users.value = listOf(UserAccount(userId, tenantId, null, "cami", "Camila Reyes", Role.CASHIER, true, hasPin = true))
        tenantRepository.pinHashesByUserId[userId] = "1234"
        viewModel.uiState.first { !it.loading }
        viewModel.onAction(AuthAction.OnStaffSelected(userId))

        repeat(4) {
            "0000".forEach { viewModel.onAction(AuthAction.OnPinDigitTapped(it.toString())) }
            advanceUntilIdle()
        }
        // The 5th miss starts the lockout ticker — its first tick runs eagerly (Unconfined
        // dispatcher) without needing advanceUntilIdle, which would otherwise drain the whole
        // 30s countdown to completion since it's a terminating delay loop.
        "0000".forEach { viewModel.onAction(AuthAction.OnPinDigitTapped(it.toString())) }

        val state = viewModel.uiState.first()
        assertTrue(state.lockRemainingSeconds > 0)

        // A correct PIN is ignored while locked out — the pad itself is disabled, but assert the
        // ViewModel enforces it too rather than trusting the UI alone. onPinDigit returns
        // synchronously (no coroutine launched) while locked, so no advanceUntilIdle needed.
        "1234".forEach { viewModel.onAction(AuthAction.OnPinDigitTapped(it.toString())) }
        assertEquals(null, sessionProvider.currentUserIdState.value)
    }
}
