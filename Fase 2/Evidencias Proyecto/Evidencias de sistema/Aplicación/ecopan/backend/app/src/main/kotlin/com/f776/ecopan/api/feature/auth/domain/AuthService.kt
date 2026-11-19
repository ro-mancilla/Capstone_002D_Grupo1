package com.f776.ecopan.api.feature.auth.domain

import com.f776.ecopan.api.feature.auth.controller.dto.LoginResponse
import com.f776.ecopan.api.feature.auth.controller.dto.RefreshResponse

sealed interface LoginOutcome {
    data class Success(val response: LoginResponse) : LoginOutcome
    data object InvalidCredentials : LoginOutcome
}

sealed interface RefreshOutcome {
    data class Success(val response: RefreshResponse) : RefreshOutcome
    data object Invalid : RefreshOutcome
}

interface AuthService {
    suspend fun login(
        tenantSlug: String,
        username: String,
        password: String,
        deviceId: String?,
        deviceName: String?
    ): LoginOutcome

    suspend fun refresh(refreshToken: String): RefreshOutcome

    /** Idempotent: revoking an already-revoked/unknown token is a no-op. */
    suspend fun logout(refreshToken: String)
}
