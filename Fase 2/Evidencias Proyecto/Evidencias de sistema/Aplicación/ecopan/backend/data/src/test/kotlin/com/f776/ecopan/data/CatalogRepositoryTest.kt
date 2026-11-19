package com.f776.ecopan.data

import com.f776.ecopan.data.database.entity.SoldBy
import com.f776.ecopan.data.database.repository.CatalogRepositoryImpl
import com.f776.ecopan.data.database.repository.TenantRepositoryImpl
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.uuid.Uuid

class CatalogRepositoryTest : RepositoryTestBase() {

    private val tenants = TenantRepositoryImpl()
    private val catalog = CatalogRepositoryImpl()

    @BeforeEach
    fun setUp() = truncateAll()

    @Test
    fun `create then find round-trips a product`() = runTest {
        val tenantId = Uuid.random()
        tenants.create(tenantId, "reference", "Reference Bakery", "CLP")

        val productId = Uuid.random()
        val created = catalog.createProduct(
            id = productId,
            tenantId = tenantId,
            name = "Marraqueta",
            productType = "BREAD",
            soldBy = SoldBy.WEIGHT,
            pricePerKgMinor = 2500,
            priceMinor = null,
            barcode = null
        )

        assertEquals("Marraqueta", created.name)

        val fetched = catalog.findProductById(productId)
        assertEquals(created, fetched)

        val updated = catalog.updateProduct(productId, name = "Marraqueta Grande")
        assertNotNull(updated)
        assertEquals("Marraqueta Grande", updated.name)
        assert(updated.serverSeq > created.serverSeq)

        val list = catalog.listProducts(tenantId)
        assertEquals(1, list.size)
    }
}
