package com.f776.ecopan.data

import com.f776.ecopan.data.database.entity.SoldBy
import com.f776.ecopan.data.database.repository.BranchRepositoryImpl
import com.f776.ecopan.data.database.repository.CatalogRepositoryImpl
import com.f776.ecopan.data.database.repository.ForecastRepositoryImpl
import com.f776.ecopan.data.database.repository.TenantRepositoryImpl
import com.f776.ecopan.data.database.entity.ProductOrigin
import com.f776.ecopan.data.domain.ProductKind
import com.f776.ecopan.data.domain.ProductTypeKey
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** The fixed product types (Pan, Pastelería, Almacén), seeded with the tenant, and their foreign keys. */
class ProductTypeLookupTest : RepositoryTestBase() {

    private val tenants = TenantRepositoryImpl()
    private val branches = BranchRepositoryImpl()
    private val catalog = CatalogRepositoryImpl()
    private val forecasts = ForecastRepositoryImpl()

    @BeforeEach
    fun setUp() = truncateAll()

    private suspend fun newTenant(): Uuid = Uuid.random().also { tenants.create(it, "t-$it", "T", "CLP") }

    private fun typeNames(tenantId: Uuid) =
        q("SELECT name FROM product_types WHERE tenant_id = '$tenantId' ORDER BY key").map { it.single() }

    private fun typeId(tenantId: Uuid, name: String) =
        q("SELECT id FROM product_types WHERE tenant_id = '$tenantId' AND name = '$name'").single().single()

    private fun q(sql: String): List<List<String?>> = transaction {
        exec(sql) { rs ->
            buildList { while (rs.next()) add((1..rs.metaData.columnCount).map { rs.getString(it) }) }
        }.orEmpty()
    }

    @Test
    fun `key ignores case accents spacing and separators`() {
        val keys = listOf("Pastelería", "PASTELERIA", " pasteleria ", "Pastelería").map(ProductTypeKey::of).toSet()
        assertEquals(setOf("pasteleria"), keys)
        assertEquals(ProductTypeKey.of("SLICED_BREAD"), ProductTypeKey.of("sliced  bread"))
    }

    @Test
    fun `a new tenant gets exactly Pan, Pastelería and Almacén, each tenant its own`() = runTest {
        val a = newTenant()
        val b = newTenant()
        assertEquals(listOf("Almacén", "Pan", "Pastelería"), typeNames(a))
        assertEquals(listOf("Almacén", "Pan", "Pastelería"), typeNames(b))
        assertTrue(typeId(a, "Pan") != typeId(b, "Pan"), "another tenant has its own list")
    }

    @Test
    fun `the fixed kinds derive the origin and match their labels by key`() {
        assertEquals(ProductKind.ALMACEN, ProductKind.fromName("almacen"))
        assertEquals(ProductKind.PASTELERIA, ProductKind.fromName(" PASTELERÍA "))
        assertNull(ProductKind.fromName("Bebidas"))
        assertEquals(
            listOf(ProductOrigin.PANADERIA, ProductOrigin.PANADERIA, ProductOrigin.ALMACEN),
            ProductKind.entries.map { it.origin }
        )
    }

    @Test
    fun `creating products and forecasts resolves variants onto one managed type`() = runTest {
        val tenantId = newTenant()
        val branch = branches.create(Uuid.random(), tenantId, "Main")
        val p1 = catalog.createProduct(Uuid.random(), tenantId, "Marraqueta", "Pan", SoldBy.WEIGHT, 1000, null, null)
        val p2 = catalog.createProduct(Uuid.random(), tenantId, "Hallulla", "PAN", SoldBy.UNIT, null, 300, null)
        assertEquals("Pan", p2.productType)
        assertEquals(3, typeNames(tenantId).size, "a variant never adds a type")

        val moved = catalog.updateProduct(p1.id, productType = "pastelería")!!
        assertEquals("Pastelería", moved.productType)
        assertEquals(3, typeNames(tenantId).size)

        forecasts.upsert(tenantId, branch.id, p1.id, LocalDate(2026, 7, 20), 1, null, "v1")
        forecasts.upsert(tenantId, branch.id, p2.id, LocalDate(2026, 7, 20), 2, null, "v2")
        assertEquals(1, forecasts.listByBranch(tenantId, branch.id, LocalDate(2026, 7, 20), LocalDate(2026, 7, 20), "PAN").size,
            "p1 moved to Pastelería; only p2 is still Pan")
        assertEquals(
            "0",
            q("SELECT count(*) FROM products WHERE product_type_id IS NULL").single().single()
        )
    }

    @Test
    fun `the foreign keys reject an unknown type id and the table rejects a duplicate key`() = runTest {
        val tenantId = newTenant()
        val panId = typeId(tenantId, "Pan")
        assertFailsWith<Exception> {
            transaction { exec("INSERT INTO product_types (id, tenant_id, name, key) VALUES ('${Uuid.random()}', '$tenantId', 'PAN', 'pan')") }
        }
        val product = catalog.createProduct(Uuid.random(), tenantId, "X", "Pan", SoldBy.UNIT, null, 1, null)
        assertEquals(panId, q("SELECT product_type_id FROM products WHERE id = '${product.id}'").single().single())
        assertFailsWith<Exception> {
            transaction { exec("UPDATE products SET product_type_id = '${Uuid.random()}' WHERE id = '${product.id}'") }
        }
    }

    private val foreignKeyViolation = "23503"

    @Test
    fun `a product cannot point at another tenant's product type`() = runTest {
        val a = newTenant()
        val b = newTenant()
        val product = catalog.createProduct(Uuid.random(), a, "X", "Pan", SoldBy.UNIT, null, 1, null)
        val othersPan = typeId(b, "Pan")
        val update = assertFailsWith<ExposedSQLException> {
            transaction { exec("UPDATE products SET product_type_id = '$othersPan' WHERE id = '${product.id}'") }
        }
        assertEquals(foreignKeyViolation, update.sqlState)

        fun insertWithType(typeId: String?) = transaction {
            exec(
                "INSERT INTO products (id, tenant_id, name, product_type, product_type_id, origin, sold_by, price_minor, server_seq) " +
                    "VALUES ('${Uuid.random()}', '$a', 'Y', 'Pan', '$typeId', 'PANADERIA', 'UNIT', 1, nextval('server_seq'))"
            )
        }
        insertWithType(typeId(a, "Pan")) // control: the same insert with its own tenant's type is fine
        val insert = assertFailsWith<ExposedSQLException> { insertWithType(othersPan) }
        assertEquals(foreignKeyViolation, insert.sqlState)
        assertEquals(typeId(a, "Pan"), q("SELECT product_type_id FROM products WHERE id = '${product.id}'").single().single())
    }

    @Test
    fun `the repository resolves a product's type inside its own tenant`() = runTest {
        val a = newTenant()
        val b = newTenant()
        // Same name in two tenants: each product must land on its own tenant's type row.
        val pa = catalog.createProduct(Uuid.random(), a, "X", "Pan", SoldBy.UNIT, null, 1, null)
        val pb = catalog.createProduct(Uuid.random(), b, "X", "Pan", SoldBy.UNIT, null, 1, null)
        assertEquals(typeId(a, "Pan"), q("SELECT product_type_id FROM products WHERE id = '${pa.id}'").single().single())
        assertEquals(typeId(b, "Pan"), q("SELECT product_type_id FROM products WHERE id = '${pb.id}'").single().single())
        // And a type change keeps it tenant-local (the composite FK would reject anything else).
        catalog.updateProduct(pa.id, productType = "Pastelería")
        assertEquals(typeId(a, "Pastelería"), q("SELECT product_type_id FROM products WHERE id = '${pa.id}'").single().single())
    }
}
