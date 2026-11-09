package com.f776.ecopan.data.database.repository

import com.f776.ecopan.data.database.entity.TenantModuleTable
import com.f776.ecopan.data.database.entity.TenantTable
import com.f776.ecopan.data.database.repository.model.Tenant
import com.f776.ecopan.data.domain.Module
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

internal class TenantRepositoryImpl : TenantRepository {

    private data class CacheEntry(val modules: Set<Module>, val cachedAt: kotlin.time.Instant)

    private val cache = java.util.concurrent.ConcurrentHashMap<Uuid, CacheEntry>()
    private val cacheTtl = 30.seconds

    override suspend fun create(
        id: Uuid,
        slug: String,
        name: String,
        currencyCode: String,
        enabledModules: Set<Module>
    ): Tenant = suspendTransaction {
        TenantTable.insert {
            it[TenantTable.id] = id
            it[TenantTable.slug] = slug
            it[TenantTable.name] = name
            it[TenantTable.currencyCode] = currencyCode
        }
        writeModules(id, enabledModules)
        ensureDefaultProductTypes(id)
        findByIdInternal(id)!!
    }

    override suspend fun findById(id: Uuid): Tenant? = suspendTransaction {
        findByIdInternal(id)
    }

    override suspend fun findBySlug(slug: String): Tenant? = suspendTransaction {
        TenantTable.selectAll().where { TenantTable.slug eq slug }.singleOrNull()?.toTenant(::modulesOf)
    }

    override suspend fun listAll(): List<Tenant> = suspendTransaction {
        TenantTable.selectAll().map { it.toTenant(::modulesOf) }
    }

    override suspend fun updateEnabledModules(id: Uuid, enabledModules: Set<Module>): Tenant? = suspendTransaction {
        if (TenantTable.selectAll().where { TenantTable.id eq id }.empty()) return@suspendTransaction null
        writeModules(id, enabledModules)
        cache.remove(id)
        findByIdInternal(id)
    }

    override suspend fun enabledModules(id: Uuid): Set<Module> {
        val now = Clock.System.now()
        cache[id]?.let { entry ->
            if (now - entry.cachedAt < cacheTtl) return entry.modules
        }
        val modules = findById(id)?.enabledModules ?: emptySet()
        cache[id] = CacheEntry(modules, now)
        return modules
    }

    private fun findByIdInternal(id: Uuid): Tenant? =
        TenantTable.selectAll().where { TenantTable.id eq id }.singleOrNull()?.toTenant(::modulesOf)

    private fun modulesOf(id: Uuid): Set<Module> =
        TenantModuleTable.selectAll().where { TenantModuleTable.tenantId eq id }.map { it[TenantModuleTable.module] }.toSet()

    /** Replaces the tenant's module rows with exactly [modules]: delete the rest, insert the missing. */
    private fun writeModules(id: Uuid, modules: Set<Module>) {
        TenantModuleTable.deleteWhere { (tenantId eq id) and (module notInList modules) }
        modules.forEach { m ->
            TenantModuleTable.insertIgnore {
                it[tenantId] = id
                it[module] = m
            }
        }
    }

    private fun ResultRow.toTenant(modulesOf: (Uuid) -> Set<Module>) = Tenant(
        id = this[TenantTable.id],
        slug = this[TenantTable.slug],
        name = this[TenantTable.name],
        currencyCode = this[TenantTable.currencyCode],
        enabledModules = modulesOf(this[TenantTable.id]),
        createdAt = this[TenantTable.createdAt]
    )
}
