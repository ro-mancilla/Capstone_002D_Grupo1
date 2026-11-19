package com.f776.ecopan.feature.auth.screens.auth

import com.f776.ecopan.core.domain.auth.Role
import com.f776.ecopan.feature.auth.DefaultDeviceName
import kotlin.uuid.Uuid

internal enum class AuthPhase { ENROLLMENT, BOOTSTRAPPING, LOCK, UNLOCKED }

internal data class AuthUiState(
    val loading: Boolean = true,
    val phase: AuthPhase = AuthPhase.ENROLLMENT,
    /** iOS has no bcrypt PIN verifier yet (01 §4, core:crypto/PinVerifier.ios.kt) — gates Lock. */
    val pinUnlockSupported: Boolean = true,
    /** Dev/prototyping convenience only — `BuildKonfig.DEV_QUICK_LOGIN_ENABLED` (data/sync/impl). */
    val devQuickLoginEnabled: Boolean = false,
    // Enrollment form
    /**
     * Prefilled with the seeded demo tenant (README) so a local stack is one tap from enrolling.
     * It used to default to `la-espiga`, which no seed profile has ever created: the backend
     * answers an unknown tenant with the same 401 as a bad password (deliberately -- it must not
     * leak which bakeries exist), so correct credentials came back as "usuario o contraseña
     * incorrectos" and the misleading part was the *bakery* field nobody had touched.
     */
    val tenantSlug: String = "reference",
    val username: String = "",
    val password: String = "",
    /** Till-shaped on the till, phone-shaped on the phone — see [DefaultDeviceName]. */
    val deviceName: String = DefaultDeviceName,
    // Enrollment result
    /** Why the last sign-in failed, or `null`. At most one at a time — string picked in the UI. */
    val enrollError: EnrollError? = null,
    // Bootstrap
    val bootstrapSteps: List<BootstrapStepUi> = emptyList(),
    val tenantName: String = "",
    // Lock
    val staff: List<StaffUi> = emptyList(),
    val selectedStaffId: Uuid? = null,
    val enteredPinLength: Int = 0,
    val pinError: Boolean = false,
    val lockRemainingSeconds: Long = 0,
)

/** A failed enrollment sign-in; the wording is picked in [AuthFlowScreen], same convention as [AuthUiState.pinError]. */
internal enum class EnrollError {
    /** [com.f776.ecopan.domain.tenant.LoginOutcome.InvalidCredentials]. */
    INVALID_CREDENTIALS,

    /** [com.f776.ecopan.domain.tenant.LoginOutcome.NetworkError] on a device with no cached tenant
     *  yet (03 §8 "First sign-in needs a connection" — a *returning* device restores its session
     *  locally instead, see [AuthViewModel]). */
    NEEDS_CONNECTION,

    /** [com.f776.ecopan.domain.tenant.LoginOutcome.TooManyAttempts]: wait a moment and try again. */
    TOO_MANY_ATTEMPTS,
}

/** Which step, not its wording — strings are picked in the UI ([BootstrapStep.label]), same
 *  convention as [AuthUiState.enrollError]. */
internal enum class BootstrapStep { ACCOUNT, INITIAL_SYNC }

internal enum class BootstrapStepNote { NONE, DONE, RETRYING_IN_BACKGROUND }

internal data class BootstrapStepUi(
    val step: BootstrapStep,
    val note: BootstrapStepNote,
    val done: Boolean,
    val active: Boolean,
)

internal data class StaffUi(
    val id: Uuid,
    val name: String,
    val role: Role,
    val initials: String,
)
