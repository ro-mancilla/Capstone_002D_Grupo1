package com.f776.ecopan.domain.tenant.wire

import kotlinx.serialization.Serializable

/**
 * Client-side mirror of `backend/app`'s `com.f776.ecopan.api.feature.auth.controller.dto.AuthDtos`
 * — that file is authoritative for field names/shapes (01 §4 device session, 03 §8 bootstrap).
 * Lives in `:data:tenant:api` (not `:data:sync:api`) because login/refresh are session concerns,
 * not sync-transport concerns — `:data:sync:impl`'s real `SyncAuthProvider` depends on
 * `:data:tenant:api` already (03 §1.2 "Depends on `:data:tenant:api` for session") so it can reuse
 * [RefreshRequestDto]/[RefreshResponseDto] from here without a new dependency edge.
 */
@Serializable
data class LoginRequestDto(
    val tenantSlug: String,
    val username: String,
    val password: String,
    val deviceId: String? = null,
    val deviceName: String? = null,
)

@Serializable
data class LoginResponseDto(
    val accessToken: String,
    val refreshToken: String,
    val user: UserSummaryDto,
    val tenant: TenantSummaryDto,
    val branches: List<BranchSummaryDto>,
)

@Serializable
data class UserSummaryDto(
    val id: String,
    val displayName: String,
    val role: String,
    val branchId: String?,
)

@Serializable
data class TenantSummaryDto(
    val id: String,
    val name: String,
    val currencyCode: String,
    val enabledModules: List<String>,
)

@Serializable
data class BranchSummaryDto(
    val id: String,
    val name: String,
)

@Serializable
data class RefreshRequestDto(val refreshToken: String)

@Serializable
data class RefreshResponseDto(val accessToken: String, val refreshToken: String)
