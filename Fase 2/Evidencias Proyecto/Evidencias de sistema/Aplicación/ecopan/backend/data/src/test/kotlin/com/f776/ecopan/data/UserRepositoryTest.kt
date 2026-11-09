package com.f776.ecopan.data

import com.f776.ecopan.data.database.repository.TenantRepositoryImpl
import com.f776.ecopan.data.database.repository.UserRepositoryImpl
import com.f776.ecopan.data.domain.Role
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.uuid.Uuid

class UserRepositoryTest : RepositoryTestBase() {

    private val tenants = TenantRepositoryImpl()
    private val users = UserRepositoryImpl()

    @BeforeEach
    fun setUp() = truncateAll()

    @Test
    fun `create then find round-trips a user with password and pin hashes present`() = runTest {
        val tenantId = Uuid.random()
        tenants.create(tenantId, "reference", "Reference Bakery", "CLP")

        val userId = Uuid.random()
        val created = users.create(
            id = userId,
            tenantId = tenantId,
            branchId = null,
            username = "admin",
            passwordHash = "bcrypt-hash-of-password",
            pinHash = "bcrypt-hash-of-pin",
            displayName = "Admin",
            role = Role.ADMIN
        )

        assertEquals("bcrypt-hash-of-password", created.passwordHash)
        assertEquals("bcrypt-hash-of-pin", created.pinHash)
        assertNotNull(created.serverSeq)

        val fetched = users.findById(userId)
        assertEquals(created, fetched)

        val byTenantAndUsername = users.findByTenantAndUsername(tenantId, "admin")
        assertEquals(created, byTenantAndUsername)
    }

    @Test
    fun `update changes fields and assigns a fresh server_seq`() = runTest {
        val tenantId = Uuid.random()
        tenants.create(tenantId, "unipan", "Unipan", "CLP")
        val userId = Uuid.random()
        val created = users.create(
            userId, tenantId, null, "cashier1", "hash1", null, "Cashier One", Role.CASHIER
        )

        val updated = users.update(userId, displayName = "Cashier Uno", active = false)

        assertNotNull(updated)
        assertEquals("Cashier Uno", updated.displayName)
        assertEquals(false, updated.active)
        assert(updated.serverSeq > created.serverSeq)
    }
}
