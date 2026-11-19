package com.f776.ecopan.api.feature.auth.controller.dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val tenantSlug: String,
    val username: String,
    val password: String,
    val deviceId: String? = null,
    val deviceName: String? = null
)

@Serializable
data class LoginResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: UserSummaryDto,
    val tenant: TenantSummaryDto,
    val branches: List<BranchSummaryDto>
)

@Serializable
data class UserSummaryDto(
    val id: String,
    val displayName: String,
    val role: String,
    val branchId: String?
)

@Serializable
data class TenantSummaryDto(
    val id: String,
    val name: String,
    val currencyCode: String,
    val enabledModules: List<String>
)

@Serializable
data class BranchSummaryDto(
    val id: String,
    val name: String
)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class RefreshResponse(val accessToken: String, val refreshToken: String)

@Serializable
data class LogoutRequest(val refreshToken: String)
