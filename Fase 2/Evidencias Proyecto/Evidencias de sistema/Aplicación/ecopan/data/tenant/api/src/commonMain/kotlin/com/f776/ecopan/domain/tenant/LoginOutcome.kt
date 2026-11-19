package com.f776.ecopan.domain.tenant

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Result of [AuthRepository.login] (01 §4 device session enrollment / 03 §8 bootstrap first
 * login). [NetworkError] deliberately covers both "no connectivity" and any other transport
 * failure — the fresh-install-offline case (03 §8 "First sign-in needs a connection") is detected
 * by the caller from this outcome rather than from a separate `ConnectivityMonitor` read, since a
 * real device-bound connectivity signal isn't guaranteed to be wired everywhere this is called.
 */
@OptIn(ExperimentalUuidApi::class)
sealed interface LoginOutcome {
    data class Success(val tenantId: Uuid, val userId: Uuid) : LoginOutcome
    data object InvalidCredentials : LoginOutcome
    /** The server is throttling sign-in attempts from this address (HTTP 429): not a wrong password, not offline. */
    data object TooManyAttempts : LoginOutcome
    data object NetworkError : LoginOutcome
}
