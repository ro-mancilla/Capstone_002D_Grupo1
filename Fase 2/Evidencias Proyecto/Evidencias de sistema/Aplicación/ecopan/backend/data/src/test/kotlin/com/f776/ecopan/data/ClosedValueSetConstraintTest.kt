package com.f776.ecopan.data

import com.f776.ecopan.data.core.DatabaseSettings
import com.f776.ecopan.data.database.ClosedValueSet
import com.f776.ecopan.data.database.applyClosedValueSet
import com.f776.ecopan.data.database.connectToDb
import com.f776.ecopan.data.database.entity.DecrementStatus
import com.f776.ecopan.data.database.entity.PaymentMethod
import com.f776.ecopan.data.database.entity.ProductStockMovementKind
import com.f776.ecopan.data.database.entity.SoldBy
import com.f776.ecopan.data.database.entity.StockMovementKind
import com.f776.ecopan.data.database.repository.BranchRepositoryImpl
import com.f776.ecopan.data.database.repository.CatalogRepositoryImpl
import com.f776.ecopan.data.database.repository.ProductStockRepositoryImpl
import com.f776.ecopan.data.database.repository.ProductionBatchRepositoryImpl
import com.f776.ecopan.data.database.repository.SaleRepositoryImpl
import com.f776.ecopan.data.database.repository.StockMovementRepositoryImpl
import com.f776.ecopan.data.database.repository.TenantRepositoryImpl
import com.f776.ecopan.data.database.repository.UserRepositoryImpl
import com.f776.ecopan.data.database.repository.model.SaleLineInput
import com.f776.ecopan.data.domain.Role
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Closed value sets: the DB itself refuses a value outside the Kotlin enum, and boot adds the CHECKs idempotently. */
class ClosedValueSetConstraintTest : RepositoryTestBase() {

    private val tenants = TenantRepositoryImpl()
    private val branches = BranchRepositoryImpl()
    private val users = UserRepositoryImpl()
    private val catalog = CatalogRepositoryImpl()
    private val sales = SaleRepositoryImpl(Clock.System)
    private val batches = ProductionBatchRepositoryImpl()
    private val movements = StockMovementRepositoryImpl()
    private val productStock = ProductStockRepositoryImpl(Clock.System)

    @BeforeEach
    fun setUp() = truncateAll()

    private fun reboot() = runBlocking {
        connectToDb(
            DatabaseSettings(postgres.host, postgres.firstMappedPort, postgres.databaseName, postgres.username, postgres.password, "org.postgresql.Driver"),
            init = true
        )
    }

    /** One row in every constrained table; returns table -> (column, a valid value) pairs to corrupt. */
    private suspend fun seedOneRowPerTable() {
        val tenantId = Uuid.random()
        tenants.create(tenantId, "reference", "Reference Bakery", "CLP")
        val branch = branches.create(Uuid.random(), tenantId, "Main")
        val user = users.create(Uuid.random(), tenantId, branch.id, "u1", "hash", null, "U", Role.BRANCH_MANAGER)
        val product = catalog.createProduct(Uuid.random(), tenantId, "Cola", "Bebidas", SoldBy.UNIT, null, 1500, null)
        val ingredientId = Uuid.random()
        transaction {
            exec("INSERT INTO ingredients (id, tenant_id, name, server_seq) VALUES ('$ingredientId', '$tenantId', 'Harina', nextval('server_seq'))")
        }
        sales.createWithLines(
            Uuid.random(), tenantId, branch.id, user.id, Clock.System.now(), PaymentMethod.CASH, 1500, "pos-1",
            listOf(SaleLineInput(Uuid.random(), product.id, SoldBy.UNIT, null, 1, 1500, 1500))
        )
        batches.create(Uuid.random(), tenantId, branch.id, user.id, product.id, 1000, Clock.System.now(), false, "d1")
        movements.append(Uuid.random(), tenantId, branch.id, ingredientId, 10, StockMovementKind.PURCHASE, null, null, user.id, Clock.System.now())
        productStock.append(Uuid.random(), tenantId, branch.id, product.id, 5, ProductStockMovementKind.PURCHASE, null, user.id, Clock.System.now())
    }

    private val constrained = listOf(
        "sales" to "payment_method",
        "products" to "sold_by",
        "products" to "origin",
        "sale_lines" to "sold_by",
        "stock_movements" to "kind",
        "product_stock_movements" to "kind",
        "production_batches" to "decrement_status",
        "role_permissions" to "permission",
        "tenant_modules" to "module"
    )

    private fun constraintState(table: String, column: String): Boolean? = transaction {
        exec(
            "SELECT convalidated FROM pg_constraint WHERE conname = 'ck_${table}_$column' AND conrelid = '$table'::regclass"
        ) { rs -> if (rs.next()) rs.getBoolean(1) else null }
    }

    @Test
    fun `the database rejects a value outside every closed set`() = runTest {
        seedOneRowPerTable()
        constrained.forEach { (table, column) ->
            assertEquals(true, constraintState(table, column), "$table.$column should carry a validated CHECK")
            assertFailsWith<Exception>("$table.$column accepted an out-of-set value") {
                transaction { exec("UPDATE $table SET $column = 'NOT_A_VALUE'") }
            }
            // lower-case is outside the set too: the constraint is case-sensitive like the enum mapping.
            assertFailsWith<Exception>("$table.$column accepted a lower-case value") {
                transaction { exec("UPDATE $table SET $column = lower($column)") }
            }
        }
        // A valid value is still fine.
        transaction { exec("UPDATE sales SET payment_method = '${PaymentMethod.CARD.name}'") }
        transaction { exec("UPDATE production_batches SET decrement_status = '${DecrementStatus.APPLIED.name}'") }
    }

    @Test
    fun `booting again is a no-op and keeps the constraints`() = runTest {
        reboot()
        reboot()
        constrained.forEach { (table, column) -> assertEquals(true, constraintState(table, column), "$table.$column") }
        val count = transaction {
            exec("SELECT count(*) FROM pg_constraint WHERE conname LIKE 'ck\\_%' ESCAPE '\\'") { rs -> rs.next(); rs.getInt(1) }
        }
        assertEquals(constrained.size, count)
    }

    @Test
    fun `a constraint whose set no longer matches the enum is recreated on boot`() = runTest {
        transaction {
            exec("ALTER TABLE products DROP CONSTRAINT ck_products_origin")
            exec("ALTER TABLE products ADD CONSTRAINT ck_products_origin CHECK (origin IN ('PANADERIA'))")
        }
        reboot()
        seedOneRowPerTable()
        transaction { exec("UPDATE products SET origin = 'ALMACEN'") }
    }

    private fun constraintOid(name: String): Long? = transaction {
        exec("SELECT oid::bigint FROM pg_constraint WHERE conname = '$name'") { rs -> if (rs.next()) rs.getLong(1) else null }
    }

    @Test
    fun `a value containing digits does not make the constraint look drifted`() = runTest {
        transaction { exec("DROP TABLE IF EXISTS cv_probe"); exec("CREATE TABLE cv_probe (v varchar(32))") }
        val set = ClosedValueSet("cv_probe", "v", listOf("PRICE_V2", "B3", "PLAIN"))
        applyClosedValueSet(set)
        val created = constraintOid(set.constraint)
        applyClosedValueSet(set)
        // Same oid: the second boot recognised the member set and did not drop and re-add.
        assertEquals(created, constraintOid(set.constraint))
        transaction { exec("INSERT INTO cv_probe VALUES ('PRICE_V2')") }
        assertFailsWith<Exception> { transaction { exec("INSERT INTO cv_probe VALUES ('PRICE_V3')") } }
        transaction { exec("DROP TABLE cv_probe") }
    }

    @Test
    fun `two replicas recreating a drifted constraint at once both succeed`() = runBlocking {
        transaction { exec("DROP TABLE IF EXISTS cv_probe"); exec("CREATE TABLE cv_probe (v varchar(32))") }
        val old = ClosedValueSet("cv_probe", "v", listOf("A"))
        applyClosedValueSet(old)
        val widened = ClosedValueSet("cv_probe", "v", listOf("A", "B"))
        val scope = this
        List(4) { scope.async(kotlinx.coroutines.Dispatchers.IO) { applyClosedValueSet(widened) } }
            .forEach { it.await() }
        transaction { exec("INSERT INTO cv_probe VALUES ('B')") }
        transaction { exec("DROP TABLE cv_probe") }
    }
}
