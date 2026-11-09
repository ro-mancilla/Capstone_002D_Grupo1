package com.f776.ecopan.data.database.repository

import com.f776.ecopan.data.database.repository.model.Tenant
import com.f776.ecopan.data.domain.Module
import kotlin.uuid.Uuid

interface TenantRepository {
    suspend fun create(
        id: Uuid,
        slug: String,
        name: String,
        currencyCode: String,
        enabledModules: Set<Module> = setOf(Module.POS_SALES)
    ): Tenant

    suspend fun findById(id: Uuid): Tenant?
    suspend fun findBySlug(slug: String): Tenant?
    suspend fun listAll(): List<Tenant>
    suspend fun updateEnabledModules(id: Uuid, enabledModules: Set<Module>): Tenant?

    /** Cached (~30s TTL per 02 §2) so `requireModule` doesn't hit the DB on every request. */
    suspend fun enabledModules(id: Uuid): Set<Module>
}
