package com.f776.unigrid.core.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.f776.ecopan.core.domain.auth.Role
import com.f776.ecopan.core.domain.tenant.EntitlementModule
import com.f776.unigrid.core.database.entity.BranchEntity
import com.f776.unigrid.core.database.entity.TenantEntity
import com.f776.unigrid.core.database.entity.UserEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Round-trip tests against a real, freshly-created (not migrated) in-memory Room database — a
 * schema smoke test for the tenant/branch/user config cache tables added in [MIGRATION_2_3].
 */
class TenantSchemaTest {

    private val database = Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .build()

    // Truncated to millis: the InstantConverter stores epoch millis (01 §3), so a raw
    // Clock.System.now() with sub-millisecond precision would never compare equal after round-trip.
    private val now = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds())
    private val tenantId = Uuid.random()

    @AfterTest
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `tenant cache holds a single row, replaced wholesale on re-upsert`() = runTest {
        val dao = database.tenantDao()
        val tenant = TenantEntity(
            id = tenantId,
            slug = "panaderia-centro",
            name = "Panadería Centro",
            currencyCode = "CLP",
            enabledModules = setOf(EntitlementModule.POS_SALES, EntitlementModule.INVENTORY_FORMULAS),
            cachedAt = now,
        )

        dao.upsert(tenant)
        assertEquals(tenant, dao.observe().first())

        val renamed = tenant.copy(name = "Panadería Centro (renamed)")
        dao.upsert(renamed)
        assertEquals(renamed, dao.observe().first())
    }

    @Test
    fun `entitlement module set round-trips through the converter`() = runTest {
        val dao = database.tenantDao()
        val allModules = EntitlementModule.entries.toSet()
        dao.upsert(
            TenantEntity(
                id = tenantId,
                slug = "unipan",
                name = "UniPan",
                currencyCode = "CLP",
                enabledModules = allModules,
                cachedAt = now,
            )
        )

        assertEquals(allModules, dao.observe().first()?.enabledModules)
    }

    @Test
    fun `branches for a tenant are observed ordered by name`() = runTest {
        val dao = database.branchDao()
        val otherTenantId = Uuid.random()
        dao.upsertAll(
            listOf(
                branch(tenantId, "Sucursal Ñuñoa"),
                branch(tenantId, "Sucursal Centro"),
                branch(otherTenantId, "Other Tenant's Branch"),
            )
        )

        val names = dao.observeByTenant(tenantId).first().map { it.name }

        assertEquals(listOf("Sucursal Centro", "Sucursal Ñuñoa"), names)
    }

    @Test
    fun `user cache stores pinHash but has no column for a password`() = runTest {
        val dao = database.userDao()
        val userId = Uuid.random()
        dao.upsertAll(
            listOf(
                UserEntity(
                    id = userId,
                    tenantId = tenantId,
                    branchId = null,
                    username = "gm",
                    displayName = "General Manager",
                    role = Role.GENERAL_MANAGER,
                    active = true,
                    pinHash = "\$2a\$12\$fakeBcryptHashForPin",
                    serverSeq = 42,
                    cachedAt = now,
                ),
            )
        )

        val cached = dao.findById(userId)
        assertEquals("\$2a\$12\$fakeBcryptHashForPin", cached?.pinHash)
        assertEquals(Role.GENERAL_MANAGER, cached?.role)
    }

    @Test
    fun `clearing a table empties it without touching the others`() = runTest {
        database.tenantDao().upsert(
            TenantEntity(tenantId, "slug", "Name", "CLP", emptySet(), now)
        )
        database.branchDao().upsertAll(listOf(branch(tenantId, "Branch")))

        database.tenantDao().clear()

        assertNull(database.tenantDao().observe().first())
        assertEquals(1, database.branchDao().observeByTenant(tenantId).first().size)
    }

    private fun branch(tenantId: Uuid, name: String) = BranchEntity(
        id = Uuid.random(),
        tenantId = tenantId,
        name = name,
        active = true,
        serverSeq = 1,
        cachedAt = now,
    )
}
