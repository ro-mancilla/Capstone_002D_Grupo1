@file:OptIn(ExperimentalUuidApi::class, ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package com.f776.ecopan.feature.auth.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.f776.ecopan.domain.sync.SyncEngine
import com.f776.ecopan.domain.tenant.AuthRepository
import com.f776.ecopan.domain.tenant.LoginOutcome
import com.f776.ecopan.domain.tenant.SessionProvider
import com.f776.ecopan.domain.tenant.TenantRepository
import com.f776.ecopan.domain.tenant.UserAccount
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val MAX_PIN_LENGTH = 4
private const val ATTEMPTS_BEFORE_LOCKOUT = 5
private val BASE_LOCKOUT = 30.seconds

/**
 * Drives the three pre-session screens (01 Enrollment / 02 Bootstrap / 03 Lock) as one reactive
 * state machine rather than NavKey destinations — none of them are back-stack entries, they gate
 * whether [com.f776.ecopan.App] renders the real navigation at all (05 §10, this session's
 * decision). [TenantRepository.observeTenant] doubles as "is this device enrolled" (its own KDoc:
 * "reads as empty until a tenant has been cached"); [SessionProvider.currentUserId] doubles as
 * "is this till currently unlocked".
 */
internal class AuthViewModel(
    private val tenantRepository: TenantRepository,
    private val authRepository: AuthRepository,
    private val syncEngine: SyncEngine,
    private val sessionProvider: SessionProvider,
    private val pinUnlockSupported: Boolean,
    private val devQuickLoginEnabled: Boolean,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AuthUiState(pinUnlockSupported = pinUnlockSupported, devQuickLoginEnabled = devQuickLoginEnabled),
    )
    val uiState = _uiState.asStateFlow()

    private var lockoutRound = 0
    private var failedAttempts = 0
    private var lockedUntil: kotlin.time.Instant? = null
    private var lockoutTickerJob: Job? = null

    private val isBootstrapping = MutableStateFlow(false)

    private val tenant = tenantRepository.observeTenant()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), null)

    private val currentUserId = sessionProvider.currentUserId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), null)

    init {
        viewModelScope.launch {
            combine(tenant, currentUserId, isBootstrapping) { t, userId, bootstrapping ->
                when {
                    bootstrapping -> AuthPhase.BOOTSTRAPPING
                    t == null -> AuthPhase.ENROLLMENT
                    userId == null -> AuthPhase.LOCK
                    else -> AuthPhase.UNLOCKED
                }
            }.combine(tenantRepository.observeUsers()) { phase, users -> phase to users }
                .collect { (phase, users) ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            phase = phase,
                            tenantName = tenant.value?.name.orEmpty(),
                            staff = if (phase == AuthPhase.LOCK) users.filter(::isUnlockCandidate).map { u -> u.toUi() } else it.staff,
                        )
                    }
                }
        }
    }

    private fun isUnlockCandidate(user: UserAccount) = user.active

    private fun UserAccount.toUi() = StaffUi(
        id = id,
        name = displayName,
        role = role,
        initials = displayName.split(" ").filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.first().uppercaseChar().toString() },
    )

    fun onAction(action: AuthAction) {
        when (action) {
            is AuthAction.OnTenantSlugChanged -> _uiState.update { it.copy(tenantSlug = action.value) }
            is AuthAction.OnUsernameChanged -> _uiState.update { it.copy(username = action.value) }
            is AuthAction.OnPasswordChanged -> _uiState.update { it.copy(password = action.value) }
            is AuthAction.OnDeviceNameChanged -> _uiState.update { it.copy(deviceName = action.value) }
            AuthAction.OnEnrollTapped -> enroll()
            is AuthAction.OnDevQuickLoginTapped -> devQuickLogin(action.profile)
            is AuthAction.OnStaffSelected -> selectStaff(action.staffId)
            AuthAction.OnBackToStaffPicker -> _uiState.update { it.copy(selectedStaffId = null, enteredPinLength = 0, pinError = false) }
            is AuthAction.OnPinDigitTapped -> onPinDigit(action.digit)
            AuthAction.OnPinBackspace -> onPinBackspace()
        }
    }

    private fun selectStaff(staffId: Uuid) {
        _uiState.update { it.copy(selectedStaffId = staffId, enteredPinLength = 0, pinError = false) }
    }

    private var enteredPin = ""

    private fun onPinBackspace() {
        val state = _uiState.value
        if (state.selectedStaffId == null || state.lockRemainingSeconds > 0) return
        enteredPin = enteredPin.dropLast(1)
        _uiState.update { it.copy(enteredPinLength = enteredPin.length, pinError = false) }
    }

    private fun onPinDigit(digit: String) {
        val state = _uiState.value
        if (state.selectedStaffId == null || state.lockRemainingSeconds > 0) return
        if (enteredPin.length >= MAX_PIN_LENGTH) return
        enteredPin += digit
        _uiState.update { it.copy(enteredPinLength = enteredPin.length, pinError = false) }
        if (enteredPin.length == MAX_PIN_LENGTH) {
            viewModelScope.launch { verifyEnteredPin(state.selectedStaffId, enteredPin) }
        }
    }

    private suspend fun verifyEnteredPin(userId: Uuid, pin: String) {
        val verified = tenantRepository.verifyPin(userId, pin)
        enteredPin = ""
        if (verified) {
            failedAttempts = 0
            lockoutRound = 0
            _uiState.update { it.copy(enteredPinLength = 0, pinError = false) }
            sessionProvider.unlock(userId)
        } else {
            failedAttempts++
            _uiState.update { it.copy(enteredPinLength = 0, pinError = true) }
            if (failedAttempts >= ATTEMPTS_BEFORE_LOCKOUT) {
                startLockout()
            }
        }
    }

    private fun startLockout() {
        failedAttempts = 0
        val duration = BASE_LOCKOUT * (1 shl lockoutRound)
        lockoutRound++
        lockedUntil = Clock.System.now() + duration
        lockoutTickerJob?.cancel()
        lockoutTickerJob = viewModelScope.launch {
            while (true) {
                val remaining = lockedUntil?.minus(Clock.System.now())?.inWholeSeconds?.coerceAtLeast(0) ?: 0
                _uiState.update { it.copy(lockRemainingSeconds = remaining) }
                if (remaining <= 0) break
                delay(1.seconds)
            }
        }
    }

    /**
     * Real device enrollment (01 §4 / 03 §8 — replaces the old fake local seeding entirely):
     * login → cache tenant/branches/this-user from the response → full sync bootstrap pull for
     * everything else (catalog, formulas, other staff, ...).
     *
     * Bootstrap-progress simplification (documented judgement call): [SyncEngine.bootstrap] and
     * [AuthRepository.login] only report success/failure, not per-page counts, so the step list is
     * two coarse steps ("Cuenta y sesión" for login, "Sincronización inicial" for the pull) rather
     * than the four illustrative fake steps the old code had — a real per-entity-type progress
     * breakdown would need `SyncEngine.bootstrap()` to expose a `Flow` of intermediate progress,
     * which is out of this item's scope (would touch `SyncEngineImpl`, mid-edit by a concurrent
     * task). A bootstrap pull failure is treated as *non-blocking*: the device is already validly
     * enrolled (tokens + this user's own profile are cached from the login response itself), so
     * the till stays usable and `SyncScheduler`'s background retries pick the rest up later —
     * consistent with 03 §3.4 "sync never blocks the till".
     */
    /** Dev/prototyping convenience only — fills the seeded profile's credentials and enrolls. */
    private fun devQuickLogin(profile: DevQuickLoginProfile) {
        _uiState.update { it.copy(tenantSlug = "reference", username = profile.username, password = profile.password) }
        enroll()
    }

    private fun enroll() {
        val state = _uiState.value
        if (state.tenantSlug.isBlank()) return
        _uiState.update { it.copy(enrollError = null) }
        isBootstrapping.value = true
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    bootstrapSteps = listOf(
                        BootstrapStepUi(BootstrapStep.ACCOUNT, BootstrapStepNote.NONE, done = false, active = true),
                        BootstrapStepUi(BootstrapStep.INITIAL_SYNC, BootstrapStepNote.NONE, done = false, active = false),
                    ),
                )
            }

            when (val outcome = authRepository.login(state.tenantSlug, state.username, state.password, state.deviceName)) {
                is LoginOutcome.Success -> {
                    _uiState.update {
                        it.copy(
                            bootstrapSteps = listOf(
                                BootstrapStepUi(BootstrapStep.ACCOUNT, BootstrapStepNote.DONE, done = true, active = false),
                                BootstrapStepUi(BootstrapStep.INITIAL_SYNC, BootstrapStepNote.NONE, done = false, active = true),
                            ),
                        )
                    }
                    val bootstrapResult = syncEngine.bootstrap()
                    _uiState.update {
                        it.copy(
                            bootstrapSteps = listOf(
                                BootstrapStepUi(BootstrapStep.ACCOUNT, BootstrapStepNote.DONE, done = true, active = false),
                                BootstrapStepUi(
                                    BootstrapStep.INITIAL_SYNC,
                                    if (bootstrapResult.success) BootstrapStepNote.DONE else BootstrapStepNote.RETRYING_IN_BACKGROUND,
                                    done = true,
                                    active = false,
                                ),
                            ),
                        )
                    }
                    isBootstrapping.value = false
                }
                LoginOutcome.InvalidCredentials -> {
                    isBootstrapping.value = false
                    _uiState.update { it.copy(enrollError = EnrollError.INVALID_CREDENTIALS) }
                }
                LoginOutcome.TooManyAttempts -> {
                    isBootstrapping.value = false
                    _uiState.update { it.copy(enrollError = EnrollError.TOO_MANY_ATTEMPTS) }
                }
                LoginOutcome.NetworkError -> {
                    isBootstrapping.value = false
                    _uiState.update { it.copy(enrollError = EnrollError.NEEDS_CONNECTION) }
                }
            }
        }
    }
}
