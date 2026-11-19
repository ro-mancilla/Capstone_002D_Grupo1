package com.f776.ecopan.data.database.repository

import com.f776.ecopan.data.database.repository.model.Branch
import kotlin.uuid.Uuid

interface BranchRepository {
    suspend fun create(id: Uuid, tenantId: Uuid, name: String): Branch
    suspend fun findById(id: Uuid): Branch?
    suspend fun listByTenant(tenantId: Uuid, includeInactive: Boolean = false): List<Branch>
    suspend fun update(id: Uuid, name: String? = null, active: Boolean? = null): Branch?
}
