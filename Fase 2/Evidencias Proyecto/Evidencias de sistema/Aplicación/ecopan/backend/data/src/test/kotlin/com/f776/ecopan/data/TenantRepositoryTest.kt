package com.f776.ecopan.data

import com.f776.ecopan.data.database.repository.TenantRepositoryImpl
import com.f776.ecopan.data.domain.Module
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class TenantRepositoryTest : RepositoryTestBase() {

    private val repository = TenantRepositoryImpl()

    @BeforeEach
    fun setUp() = truncateAll()

    @Test
    fun `create then find round-trips a tenant`() = runTest {
        val id = Uuid.random()
        val created = repository.create(
            id = id,
            slug = "reference",
            name = "Reference Bakery",
            currencyCode = "CLP",
            enabledModules = setOf(Module.POS_SALES, Module.INVENTORY_FORMULAS)
        )

        assertEquals(id, created.id)
        assertEquals(setOf(Module.POS_SALES, Module.INVENTORY_FORMULAS), created.enabledModules)

        val fetched = repository.findById(id)
        assertEquals(created, fetched)

        val bySlug = repository.findBySlug("reference")
        assertEquals(created, bySlug)
    }

    @Test
    fun `updateEnabledModules persists and is reflected by the cached accessor`() = runTest {
        val id = Uuid.random()
        repository.create(id, "unipan", "Unipan", "CLP", setOf(Module.POS_SALES))

        repository.updateEnabledModules(id, setOf(Module.POS_SALES, Module.ANALYTICS_DASHBOARD))

        val modules = repository.enabledModules(id)
        assertTrue(Module.ANALYTICS_DASHBOARD in modules)
    }
}
