package com.f776.ecopan.data

import com.f776.ecopan.data.database.connectToDb
import com.f776.ecopan.data.database.entity.SoldBy
import com.f776.ecopan.data.database.repository.CatalogRepositoryImpl
import com.f776.ecopan.data.database.repository.TenantRepositoryImpl
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** Booting (`connectToDb(init = true)`) an already-booted database that predates the composite FK. */
class TenantScopedProductTypeBootTest : RepositoryTestBase() {

    private val tenants = TenantRepositoryImpl()
    private val catalog = CatalogRepositoryImpl()

    @BeforeEach
    fun setUp() = truncateAll()

    private fun q(sql: String): List<List<String?>> = transaction {
        exec(sql) { rs ->
            buildList { while (rs.next()) add((1..rs.metaData.columnCount).map { rs.getString(it) }) }
        }.orEmpty()
    }

    private fun sql(vararg statements: String) = transaction { statements.forEach { exec(it) } }

    private fun compositeFkOid() =
        q("SELECT oid FROM pg_constraint WHERE conname = 'fk_products_tenant_product_type'").singleOrNull()?.single()

    private fun oldFkPresent() =
        q("SELECT 1 FROM pg_constraint WHERE conname = 'fk_products_product_type_id__id'").isNotEmpty()

    private fun indexPresent(name: String) = q("SELECT 1 FROM pg_indexes WHERE indexname = '$name'").isNotEmpty()

    /** Rewinds the schema to what a database booted before the composite FK looks like. */
    private fun downgradeToOldShape() = sql(
        // `forecasts` also has a composite FK onto the unique constraint dropped below; it is a
        // regenerable cache that boot recreates, so it goes first.
        "DROP TABLE IF EXISTS forecasts",
        "ALTER TABLE products DROP CONSTRAINT IF EXISTS fk_products_tenant_product_type",
        "ALTER TABLE product_types DROP CONSTRAINT IF EXISTS uq_product_types_tenant_id", // Exposed declares it as a constraint
        "DROP INDEX IF EXISTS uq_product_types_tenant_id",
        "DROP INDEX IF EXISTS idx_sales_tenant_branch_receivedat",
        "ALTER TABLE products ADD CONSTRAINT fk_products_product_type_id__id " +
            "FOREIGN KEY (product_type_id) REFERENCES product_types (id)"
    )

    @Test
    fun `an old-shape database is upgraded on boot and a second boot changes nothing`() = runTest {
        downgradeToOldShape()
        assertNull(compositeFkOid())

        connectToDb(settings(), init = true)

        val upgraded = compositeFkOid()
        assertTrue(upgraded != null, "composite FK added")
        assertTrue(indexPresent("uq_product_types_tenant_id"))
        assertTrue(indexPresent("idx_sales_tenant_branch_receivedat"))
        assertTrue(!oldFkPresent(), "the single-column FK is gone")
        val shape = q("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'fk_products_tenant_product_type'")
            .single().single()!!
        assertTrue(shape.contains("(tenant_id, product_type_id)") && shape.contains("product_types(tenant_id, id)"), shape)

        connectToDb(settings(), init = true)
        assertEquals(upgraded, compositeFkOid(), "left alone (same constraint); was: $shape")
    }

    @Test
    fun `a database holding a cross-tenant product fails boot with a readable message`() = runTest {
        val a = Uuid.random().also { tenants.create(it, "a-$it", "A", "CLP") }
        val b = Uuid.random().also { tenants.create(it, "b-$it", "B", "CLP") }
        val product = catalog.createProduct(Uuid.random(), a, "X", "Pan", SoldBy.UNIT, null, 1, null)
        val othersPan = q("SELECT id FROM product_types WHERE tenant_id = '$b' AND name = 'Pan'").single().single()
        downgradeToOldShape()
        try {
            sql("UPDATE products SET product_type_id = '$othersPan' WHERE id = '${product.id}'")

            val failure = assertFailsWith<IllegalStateException> { connectToDb(settings(), init = true) }
            assertTrue(failure.message!!.contains("1 product row(s) reference a product type of another tenant"), failure.message)
            assertNull(compositeFkOid(), "a failed boot leaves the old shape untouched")
        } finally {
            sql("DELETE FROM products WHERE id = '${product.id}'")
            connectToDb(settings(), init = true)
        }
        assertTrue(compositeFkOid() != null)
    }
}
