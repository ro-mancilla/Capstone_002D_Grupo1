package com.f776.ecopan.api.feature.tenant.controller.dto

import kotlinx.serialization.Serializable

@Serializable
data class TenantProfileResponse(
    val id: String,
    val name: String,
    val slug: String,
    val currencyCode: String,
    val enabledModules: List<String>,
    val branches: List<BranchDto>
)

@Serializable
data class BranchDto(val id: String, val name: String, val active: Boolean)

@Serializable
data class UpdateModulesRequest(val enabledModules: List<String>)

@Serializable
data class UpsertBranchRequest(val name: String, val active: Boolean = true)

@Serializable
data class UserListItemDto(
    val id: String,
    val username: String,
    val displayName: String,
    val role: String,
    val branchId: String?,
    val active: Boolean
)

@Serializable
data class UpsertUserRequest(
    val username: String,
    val password: String? = null,
    val pin: String? = null,
    val displayName: String,
    val role: String,
    val branchId: String? = null,
    val active: Boolean = true
)
