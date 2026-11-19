package com.f776.ecopan.api.feature.tenant.domain

import com.f776.ecopan.api.core.ServiceOutcome
import com.f776.ecopan.api.feature.tenant.controller.dto.BranchDto
import com.f776.ecopan.api.feature.tenant.controller.dto.TenantProfileResponse
import com.f776.ecopan.api.feature.tenant.controller.dto.UpdateModulesRequest
import com.f776.ecopan.api.feature.tenant.controller.dto.UpsertBranchRequest
import com.f776.ecopan.api.feature.tenant.controller.dto.UpsertUserRequest
import com.f776.ecopan.api.feature.tenant.controller.dto.UserListItemDto
import kotlin.uuid.Uuid

interface TenantService {
    suspend fun getProfile(tenantId: Uuid): TenantProfileResponse?

    suspend fun updateModules(tenantId: Uuid, request: UpdateModulesRequest): ServiceOutcome<TenantProfileResponse>

    suspend fun listUsers(tenantId: Uuid): List<UserListItemDto>

    suspend fun createUser(tenantId: Uuid, request: UpsertUserRequest): ServiceOutcome<UserListItemDto>

    suspend fun updateUser(tenantId: Uuid, userId: Uuid, request: UpsertUserRequest): ServiceOutcome<UserListItemDto>

    suspend fun listBranches(tenantId: Uuid, includeInactive: Boolean): List<BranchDto>

    suspend fun createBranch(tenantId: Uuid, request: UpsertBranchRequest): BranchDto

    suspend fun updateBranch(tenantId: Uuid, branchId: Uuid, request: UpsertBranchRequest): BranchDto?
}
