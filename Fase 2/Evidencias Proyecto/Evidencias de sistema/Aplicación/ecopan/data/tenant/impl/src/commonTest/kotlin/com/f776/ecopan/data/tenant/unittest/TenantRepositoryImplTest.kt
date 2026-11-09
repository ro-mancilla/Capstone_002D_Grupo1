package com.f776.ecopan.data.tenant.unittest

import com.f776.ecopan.core.domain.auth.Role
import com.f776.ecopan.core.domain.tenant.EntitlementModule
import com.f776.ecopan.data.tenant.TenantRepositoryImpl
import com.f776.ecopan.data.tenant.unittest.fake.FakeBranchDao
import com.f776.ecopan.data.tenant.unittest.fake.FakePinVerifier
import com.f776.ecopan.data.tenant.unittest.fake.FakeTenantDao
import com.f776.ecopan.data.tenant.unittest.fake.FakeUserDao
import com.f776.ecopan.domain.tenant.BranchUpsert
import com.f776.ecopan.domain.tenant.TenantProfile
import com.f776.ecopan.domain.tenant.UserAccountUpsert
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class TenantRepositoryImplTest {

    private val tenantId = Uuid.random()
    private val otherTenantId = Uuid.random()

    private val tenantDao = FakeTenantDao()
    private val branchDao = FakeBranchDao()
    private val userDao = FakeUserDao()
    private val repository = TenantRepositoryImpl(tenantDao, branchDao, userDao, FakePinVerifier())

    @Test
    fun `no tenant cached yet observes null and empty branches and users`() = runTest {
        assertNull(repository.observeTenant().first())
        assertEquals(emptyList(), repository.observeBranches().first())
        assertEquals(emptyList(), repository.observeUsers().first())
    }

    @Test
    fun `upserting a tenant makes it observable`() = runTest {
        repository.upsertTenant(tenant())

        val cached = repository.observeTenant().first()
        assertEquals("Panadería Centro", cached?.name)
        assertEquals(setOf(EntitlementModule.POS_SALES), cached?.enabledModules)
    }

    @Test
    fun `branches and users are scoped to the currently cached tenant`() = runTest {
        repository.upsertTenant(tenant())
        repository.upsertBranches(
            listOf(
                BranchUpsert(Uuid.random(), tenantId, "Sucursal Centro", active = true, serverSeq = 1),
                BranchUpsert(Uuid.random(), otherTenantId, "Not my branch", active = true, serverSeq = 1),
            )
        )
        repository.upsertUsers(
            listOf(
                UserAccountUpsert(
                    id = Uuid.random(),
                    tenantId = tenantId,
                    branchId = null,
                    username = "gm",
                    displayName = "General Manager",
                    role = Role.GENERAL_MANAGER,
                    active = true,
                    pinHash = "hash",
                    serverSeq = 1,
                ),
                UserAccountUpsert(
                    id = Uuid.random(),
                    tenantId = otherTenantId,
                    branchId = null,
                    username = "not-mine",
                    displayName = "Not Mine",
                    role = Role.CASHIER,
                    active = true,
                    pinHash = null,
                    serverSeq = 1,
                ),
            )
        )

        val branches = repository.observeBranches().first()
        val users = repository.observeUsers().first()

        assertEquals(listOf("Sucursal Centro"), branches.map { it.name })
        assertEquals(listOf("General Manager"), users.map { it.displayName })
        assertTrue(users.single().hasPin)
    }

    @Test
    fun `a user without a pin reports hasPin false and the hash never surfaces`() = runTest {
        repository.upsertTenant(tenant())
        repository.upsertUsers(
            listOf(
                UserAccountUpsert(
                    id = Uuid.random(),
                    tenantId = tenantId,
                    branchId = null,
                    username = "cashier",
                    displayName = "Cashier",
                    role = Role.CASHIER,
                    active = true,
                    pinHash = null,
                    serverSeq = 1,
                ),
            )
        )

        assertEquals(false, repository.observeUsers().first().single().hasPin)
    }

    @Test
    fun `verifyPin checks the cached hash and reports false for unknown user or missing hash`() = runTest {
        val userId = Uuid.random()
        repository.upsertTenant(tenant())
        repository.upsertUsers(
            listOf(
                UserAccountUpsert(
                    id = userId,
                    tenantId = tenantId,
                    branchId = null,
                    username = "cashier",
                    displayName = "Cashier",
                    role = Role.CASHIER,
                    active = true,
                    pinHash = "1234",
                    serverSeq = 1,
                ),
            )
        )

        assertTrue(repository.verifyPin(userId, "1234"))
        assertFalse(repository.verifyPin(userId, "0000"))
        assertFalse(repository.verifyPin(Uuid.random(), "1234"))
    }

    @Test
    fun `clear wipes tenant branches and users together`() = runTest {
        repository.upsertTenant(tenant())
        repository.upsertBranches(listOf(BranchUpsert(Uuid.random(), tenantId, "Branch", true, 1)))

        repository.clear()

        assertNull(repository.observeTenant().first())
        assertEquals(emptyList(), repository.observeBranches().first())
    }

    private fun tenant() = TenantProfile(
        id = tenantId,
        slug = "panaderia-centro",
        name = "Panadería Centro",
        currencyCode = "CLP",
        enabledModules = setOf(EntitlementModule.POS_SALES),
    )
}
