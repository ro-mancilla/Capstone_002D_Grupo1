package com.f776.ecopan.data.database.repository

import com.f776.ecopan.data.database.repository.model.User
import com.f776.ecopan.data.domain.Role
import kotlin.uuid.Uuid

interface UserRepository {
    suspend fun create(
        id: Uuid,
        tenantId: Uuid,
        branchId: Uuid?,
        username: String,
        passwordHash: String,
        pinHash: String?,
        displayName: String,
        role: Role
    ): User

    suspend fun findById(id: Uuid): User?
    suspend fun findByTenantAndUsername(tenantId: Uuid, username: String): User?
    suspend fun listByTenant(tenantId: Uuid): List<User>

    suspend fun update(
        id: Uuid,
        passwordHash: String? = null,
        pinHash: String? = null,
        displayName: String? = null,
        role: Role? = null,
        branchId: Uuid? = null,
        clearBranchId: Boolean = false,
        active: Boolean? = null
    ): User?
}
