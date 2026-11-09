package com.f776.ecopan.data

import com.f776.ecopan.data.core.DatabaseSettings
import com.f776.ecopan.data.database.connectToDb
import com.f776.ecopan.data.database.repository.BranchRepositoryImpl
import com.f776.ecopan.data.database.repository.RolePermissionRepositoryImpl
import com.f776.ecopan.data.database.repository.TenantRepositoryImpl
import com.f776.ecopan.data.database.repository.UserRepositoryImpl
import com.f776.ecopan.data.domain.Permission
import com.f776.ecopan.data.domain.Role
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** Roles are a seeded lookup referenced by foreign key; permission names are a closed set. */
class RoleLookupConstraintTest : RepositoryTestBase() {

    private val tenants = TenantRepositoryImpl()
    private val branches = BranchRepositoryImpl()
    private val users = UserRepositoryImpl()
    private val grid = RolePermissionRepositoryImpl()

    @BeforeEach
    fun setUp() = truncateAll()

    private fun reboot() = runBlocking {
        connectToDb(
            DatabaseSettings(postgres.host, postgres.firstMappedPort, postgres.databaseName, postgres.username, postgres.password, "org.postgresql.Driver"),
            init = true
        )
    }

    private fun roleNames(): Set<String> = transaction {
        exec("SELECT name FROM roles") { rs -> buildSet { while (rs.next()) add(rs.getString(1)) } }.orEmpty()
    }

    private suspend fun newUser(): Uuid {
        val tenantId = Uuid.random()
        tenants.create(tenantId, "t-${tenantId}", "T", "CLP")
        val branch = branches.create(Uuid.random(), tenantId, "Main")
        return users.create(Uuid.random(), tenantId, branch.id, "u1", "hash", null, "U", Role.CASHIER).id
    }

    @Test
    fun `roles table is seeded with exactly the Role enum`() {
        assertEquals(Role.entries.map { it.name }.toSet(), roleNames())
    }

    @Test
    fun `the seeded grid loads back and uses only known roles and permissions`() = runTest {
        val loaded = grid.loadAll()
        assertTrue(Permission.SYNC in loaded.getValue(Role.CASHIER))
        assertTrue(Role.ADMIN !in loaded)
    }

    @Test
    fun `users and role permissions reject an unknown role`() = runTest {
        val userId = newUser()
        assertFailsWith<Exception> { transaction { exec("UPDATE users SET role = 'SUPERHERO' WHERE id = '$userId'") } }
        assertFailsWith<Exception> {
            transaction { exec("INSERT INTO role_permissions (role, permission) VALUES ('SUPERHERO', 'SYNC')") }
        }
    }

    @Test
    fun `role_permissions rejects an unknown permission`() {
        assertFailsWith<Exception> {
            transaction { exec("INSERT INTO role_permissions (role, permission) VALUES ('CASHIER', 'LAUNCH_ROCKETS')") }
        }
    }

    @Test
    fun `booting again is a no-op`() {
        reboot()
        reboot()
        assertEquals(Role.entries.size, roleNames().size)
    }
}
