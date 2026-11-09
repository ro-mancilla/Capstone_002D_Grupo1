package com.f776.ecopan.data

import com.f776.ecopan.data.database.entity.PaymentMethod
import com.f776.ecopan.data.database.entity.ProductStockMovementKind
import com.f776.ecopan.data.database.entity.ProductOrigin
import com.f776.ecopan.data.database.entity.SoldBy
import com.f776.ecopan.data.database.repository.BranchRepositoryImpl
import com.f776.ecopan.data.database.repository.CatalogRepositoryImpl
import com.f776.ecopan.data.database.repository.OpeningCount
import com.f776.ecopan.data.database.repository.ProductStockRepositoryImpl
import com.f776.ecopan.data.database.repository.SaleRepositoryImpl
import com.f776.ecopan.data.database.repository.TenantRepositoryImpl
import com.f776.ecopan.data.database.repository.UserRepositoryImpl
import com.f776.ecopan.data.database.repository.model.IngestResult
import com.f776.ecopan.data.database.repository.model.SaleInput
import com.f776.ecopan.data.database.repository.model.SaleLineInput
import com.f776.ecopan.data.domain.Role
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

/** Resold-product unit ledger: Σ movements per product, and sales counted only from the first movement on. */
class ProductStockRepositoryTest : RepositoryTestBase() {

    private val tenants = TenantRepositoryImpl()
    private val branches = BranchRepositoryImpl()
    private val users = UserRepositoryImpl()
    private val catalog = CatalogRepositoryImpl()
    private val clock = TestClock()
    private val sales = SaleRepositoryImpl(clock)
    private val productStock = ProductStockRepositoryImpl(clock)

    @BeforeEach
    fun setUp() = truncateAll()

    @Test
    fun `sum by product adds every movement and reports the opening count instant`() = runTest {
        val tenantId = Uuid.random()
        tenants.create(tenantId, "reference", "Reference Bakery", "CLP")
        val branch = branches.create(Uuid.random(), tenantId, "Main")
        val manager = users.create(Uuid.random(), tenantId, branch.id, "bm1", "hash", null, "Branch Manager", Role.BRANCH_MANAGER)
        val cola = catalog.createProduct(Uuid.random(), tenantId, "Bebida cola 500ml", "Bebidas", SoldBy.UNIT, null, 1500, null)

        // Entered now, but the shelf was counted two hours earlier: the tracking instant is the entry.
        val entered = clock.now()
        val first = entered - 2.hours
        productStock.append(Uuid.random(), tenantId, branch.id, cola.id, 10, ProductStockMovementKind.OPENING_COUNT, null, manager.id, first)
        clock.advance(1.hours)
        productStock.append(Uuid.random(), tenantId, branch.id, cola.id, 14, ProductStockMovementKind.PURCHASE, "Factura 1", manager.id, first + 1.hours)
        // A product with movements but no opening count has no tracking instant of its own.
        val water = catalog.createProduct(Uuid.random(), tenantId, "Agua", "Bebidas", SoldBy.UNIT, null, 800, null)
        productStock.append(Uuid.random(), tenantId, branch.id, water.id, 6, ProductStockMovementKind.PURCHASE, null, manager.id, first)
        productStock.append(Uuid.random(), tenantId, branch.id, cola.id, -2, ProductStockMovementKind.MANUAL_ADJUSTMENT, "rotas", manager.id, Clock.System.now())

        val sums = productStock.sumByProduct(tenantId, branch.id)
        val colaSum = sums.single { it.productId == cola.id }
        assertEquals(22, colaSum.receivedUnits)
        assertEquals(entered.toEpochMilliseconds(), colaSum.openingReceivedAt!!.toEpochMilliseconds())
        assertEquals(null, sums.single { it.productId == water.id }.openingReceivedAt)
    }

    @Test
    fun `sold units count by server receipt time, not by when the sale was rung up`() = runTest {
        val tenantId = Uuid.random()
        tenants.create(tenantId, "reference", "Reference Bakery", "CLP")
        val branch = branches.create(Uuid.random(), tenantId, "Main")
        val cashier = users.create(Uuid.random(), tenantId, branch.id, "c1", "hash", null, "Cashier", Role.CASHIER)
        val cola = catalog.createProduct(Uuid.random(), tenantId, "Bebida cola 500ml", "Bebidas", SoldBy.UNIT, null, 1500, null)

        suspend fun ingest(quantity: Int, soldAt: kotlin.time.Instant) = sales.createWithLines(
            Uuid.random(), tenantId, branch.id, cashier.id, soldAt, PaymentMethod.CASH, 1500L * quantity, "till-1",
            listOf(SaleLineInput(Uuid.random(), cola.id, SoldBy.UNIT, null, quantity, 1500, 1500L * quantity))
        )

        // Received before the opening count: not subtracted, whatever its soldAt says.
        ingest(10, clock.now() + 1.hours)
        clock.advance(1.hours)
        val openingAt = clock.now()
        // Rung up offline before the count, synced after it: subtracted.
        ingest(3, openingAt - 1.hours)
        ingest(4, openingAt + 1.hours)

        assertEquals(mapOf(cola.id to 7L), productStock.soldUnitsSince(tenantId, branch.id, mapOf(cola.id to openingAt)))
        // Each product is cut off at its own instant, in the same single query.
        val water = catalog.createProduct(Uuid.random(), tenantId, "Agua", "Bebidas", SoldBy.UNIT, null, 800, null)
        sales.createWithLines(
            Uuid.random(), tenantId, branch.id, cashier.id, openingAt - 5.hours, PaymentMethod.CASH, 800, "till-1",
            listOf(SaleLineInput(Uuid.random(), water.id, SoldBy.UNIT, null, 2, 400, 800))
        )
        val batch = productStock.soldUnitsSince(
            tenantId, branch.id, mapOf(cola.id to openingAt - 2.hours, water.id to openingAt, Uuid.random() to openingAt)
        )
        assertEquals(mapOf(cola.id to 17L, water.id to 2L), batch)
    }

    @Test
    fun `a backdated opening count does not subtract sales received before it was entered`() = runTest {
        val tenantId = Uuid.random()
        tenants.create(tenantId, "reference", "Reference Bakery", "CLP")
        val branch = branches.create(Uuid.random(), tenantId, "Main")
        val cashier = users.create(Uuid.random(), tenantId, branch.id, "c1", "hash", null, "Cashier", Role.CASHIER)
        val cola = catalog.createProduct(Uuid.random(), tenantId, "Bebida cola 500ml", "Bebidas", SoldBy.UNIT, null, 1500, null)

        suspend fun sell(quantity: Int) = sales.createWithLines(
            Uuid.random(), tenantId, branch.id, cashier.id, clock.now(), PaymentMethod.CASH, 1500L * quantity, "till-1",
            listOf(SaleLineInput(Uuid.random(), cola.id, SoldBy.UNIT, null, quantity, 1500, 1500L * quantity))
        )

        // Sold and received at T0; the count is entered at T0+1h but dated 2h back (before that sale).
        sell(5)
        clock.advance(1.hours)
        productStock.append(
            Uuid.random(), tenantId, branch.id, cola.id, 20, ProductStockMovementKind.OPENING_COUNT, null, null,
            clock.now() - 2.hours
        )
        sell(2)

        val openingAt = productStock.sumByProduct(tenantId, branch.id).single().openingReceivedAt!!
        // The 5 sold before entry are already reflected in the shelf count; only the 2 after are subtracted.
        assertEquals(mapOf(cola.id to 2L), productStock.soldUnitsSince(tenantId, branch.id, mapOf(cola.id to openingAt)))
    }

    @Test
    fun `re-ingesting a sale keeps its original receipt time`() = runTest {
        val tenantId = Uuid.random()
        tenants.create(tenantId, "reference", "Reference Bakery", "CLP")
        val branch = branches.create(Uuid.random(), tenantId, "Main")
        val cashier = users.create(Uuid.random(), tenantId, branch.id, "c1", "hash", null, "Cashier", Role.CASHIER)
        val cola = catalog.createProduct(Uuid.random(), tenantId, "Bebida cola 500ml", "Bebidas", SoldBy.UNIT, null, 1500, null)
        val saleId = Uuid.random()
        val line = SaleLineInput(Uuid.random(), cola.id, SoldBy.UNIT, null, 1, 1500, 1500)

        suspend fun ingest() =
            sales.createWithLines(saleId, tenantId, branch.id, cashier.id, clock.now(), PaymentMethod.CASH, 1500, "till-1", listOf(line))
        val first = (ingest() as IngestResult.Applied).value.receivedAt
        clock.advance(3.hours)
        val again = ingest()

        assertIs<IngestResult.Duplicate<*>>(again)
        assertEquals(first, sales.findById(saleId)!!.receivedAt)
    }

    @Test
    fun `seeded history stays out of stock after a later opening count`() = runTest {
        val tenantId = Uuid.random()
        tenants.create(tenantId, "reference", "Reference Bakery", "CLP")
        val branch = branches.create(Uuid.random(), tenantId, "Main")
        val cashier = users.create(Uuid.random(), tenantId, branch.id, "c1", "hash", null, "Cashier", Role.CASHIER)
        val cola = catalog.createProduct(Uuid.random(), tenantId, "Bebida cola 500ml", "Bebidas", SoldBy.UNIT, null, 1500, null)
        sales.createAllWithLines(
            listOf(
                SaleInput(
                    Uuid.random(), tenantId, branch.id, cashier.id, clock.now() - 10.hours, PaymentMethod.CASH, 1500, "seed",
                    listOf(SaleLineInput(Uuid.random(), cola.id, SoldBy.UNIT, null, 4, 1500, 1500))
                )
            )
        )
        productStock.append(Uuid.random(), tenantId, branch.id, cola.id, 20, ProductStockMovementKind.OPENING_COUNT, null, null, clock.now())
        val openingAt = productStock.sumByProduct(tenantId, branch.id).single().openingReceivedAt!!

        assertEquals(emptyMap(), productStock.soldUnitsSince(tenantId, branch.id, mapOf(cola.id to openingAt)))
    }

    @Test
    fun `a seeded sale is received when it was sold`() = runTest {
        val tenantId = Uuid.random()
        tenants.create(tenantId, "reference", "Reference Bakery", "CLP")
        val branch = branches.create(Uuid.random(), tenantId, "Main")
        val cashier = users.create(Uuid.random(), tenantId, branch.id, "c1", "hash", null, "Cashier", Role.CASHIER)
        val cola = catalog.createProduct(Uuid.random(), tenantId, "Bebida cola 500ml", "Bebidas", SoldBy.UNIT, null, 1500, null)
        val soldAt = Clock.System.now() - 10.hours
        val saleId = Uuid.random()
        sales.createAllWithLines(
            listOf(
                SaleInput(
                    saleId, tenantId, branch.id, cashier.id, soldAt, PaymentMethod.CASH, 1500, "seed",
                    listOf(SaleLineInput(Uuid.random(), cola.id, SoldBy.UNIT, null, 1, 1500, 1500))
                )
            )
        )
        assertEquals(soldAt.toEpochMilliseconds(), sales.findById(saleId)!!.receivedAt.toEpochMilliseconds())
    }

    @Test
    fun `append is idempotent by id`() = runTest {
        val tenantId = Uuid.random()
        tenants.create(tenantId, "reference", "Reference Bakery", "CLP")
        val branch = branches.create(Uuid.random(), tenantId, "Main")
        val cola = catalog.createProduct(Uuid.random(), tenantId, "Bebida cola 500ml", "Bebidas", SoldBy.UNIT, null, 1500, null)
        val id = Uuid.random()

        val first = productStock.append(id, tenantId, branch.id, cola.id, 6, ProductStockMovementKind.PURCHASE, null, null, Clock.System.now())
        val second = productStock.append(id, tenantId, branch.id, cola.id, 6, ProductStockMovementKind.PURCHASE, null, null, Clock.System.now())

        assertIs<IngestResult.Applied<*>>(first)
        assertIs<IngestResult.Duplicate<*>>(second)
        assertEquals(6, productStock.sumByProduct(tenantId, branch.id).single().receivedUnits)
    }

    @Test
    fun `selling several Almacen products bumps their seqs in id order`() = runTest {
        val tenantId = Uuid.random()
        tenants.create(tenantId, "reference", "Reference Bakery", "CLP")
        val branch = branches.create(Uuid.random(), tenantId, "Main")
        val cashier = users.create(Uuid.random(), tenantId, branch.id, "c1", "hash", null, "Cashier", Role.CASHIER)
        val (low, high) = listOf(Uuid.random(), Uuid.random()).sorted()
        listOf(low, high).forEach {
            catalog.createProduct(it, tenantId, "P$it", "Bebidas", SoldBy.UNIT, null, 100, null, origin = ProductOrigin.ALMACEN)
        }
        // Lines name the higher id first: the bump must still lock/update the lower id first.
        sales.createWithLines(
            Uuid.random(), tenantId, branch.id, cashier.id, Clock.System.now(), PaymentMethod.CASH, 200, "till-1",
            listOf(
                SaleLineInput(Uuid.random(), high, SoldBy.UNIT, null, 1, 100, 100),
                SaleLineInput(Uuid.random(), low, SoldBy.UNIT, null, 1, 100, 100)
            )
        )
        assertTrue(catalog.findProductById(low)!!.serverSeq < catalog.findProductById(high)!!.serverSeq)
    }

    @Test
    fun `a failing opening count leaves no product behind`() = runTest {
        val tenantId = Uuid.random()
        tenants.create(tenantId, "reference", "Reference Bakery", "CLP")
        val id = Uuid.random()
        // A branch that does not exist violates the ledger's foreign key after the product row is written.
        kotlin.test.assertFailsWith<Exception> {
            catalog.createProduct(
                id, tenantId, "Cola", "Bebidas", SoldBy.UNIT, null, 100, null, origin = ProductOrigin.ALMACEN,
                opening = OpeningCount(Uuid.random(), Uuid.random(), 5, null, Clock.System.now())
            )
        }
        assertEquals(null, catalog.findProductById(id))
    }

    @Test
    fun `an opening count is written with the product`() = runTest {
        val tenantId = Uuid.random()
        tenants.create(tenantId, "reference", "Reference Bakery", "CLP")
        val branch = branches.create(Uuid.random(), tenantId, "Main")
        val id = Uuid.random()
        catalog.createProduct(
            id, tenantId, "Cola", "Bebidas", SoldBy.UNIT, null, 100, null, origin = ProductOrigin.ALMACEN,
            opening = OpeningCount(Uuid.random(), branch.id, 5, null, Clock.System.now())
        )
        assertEquals(5, productStock.sumByProduct(tenantId, branch.id).single().receivedUnits)
    }
}
