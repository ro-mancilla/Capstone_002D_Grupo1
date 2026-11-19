package com.f776.ecopan.data

import com.f776.ecopan.data.database.repository.BranchRepositoryImpl
import com.f776.ecopan.data.database.repository.IngredientRepositoryImpl
import com.f776.ecopan.data.database.repository.SyncPullRepositoryImpl
import com.f776.ecopan.data.database.repository.TenantRepositoryImpl
import com.f776.ecopan.data.database.entity.PaymentMethod
import com.f776.ecopan.data.database.entity.ProductOrigin
import com.f776.ecopan.data.database.entity.ProductStockMovementKind
import com.f776.ecopan.data.database.entity.SoldBy
import com.f776.ecopan.data.database.repository.CatalogRepositoryImpl
import com.f776.ecopan.data.database.repository.ProductStockRepositoryImpl
import com.f776.ecopan.data.database.repository.SaleRepositoryImpl
import com.f776.ecopan.data.database.repository.UserRepositoryImpl
import com.f776.ecopan.data.database.repository.model.Product
import com.f776.ecopan.data.database.repository.model.SaleLineInput
import com.f776.ecopan.data.domain.Role
import com.f776.ecopan.data.domain.SyncEntityType
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 02 §8 AC: pull query returns rows > cursor in order; hasMore/nextCursor paging correctness. */
class SyncPullRepositoryTest : RepositoryTestBase() {

    private val tenants = TenantRepositoryImpl()
    private val branches = BranchRepositoryImpl()
    private val ingredients = IngredientRepositoryImpl()
    private val pull = SyncPullRepositoryImpl()
    private val users = UserRepositoryImpl()
    private val catalog = CatalogRepositoryImpl()
    private val sales = SaleRepositoryImpl(Clock.System)
    private val productStock = ProductStockRepositoryImpl(Clock.System)

    @BeforeEach
    fun setUp() = truncateAll()

    @Test
    fun `pull merges across tables, ascending by server_seq, and pages correctly`() = runTest {
        val tenantId = Uuid.random()
        tenants.create(tenantId, "reference", "Reference Bakery", "CLP")

        // Five synced rows across two tables, interleaved.
        val b1 = branches.create(Uuid.random(), tenantId, "Branch 1")
        val i1 = ingredients.create(Uuid.random(), tenantId, "Flour", null)
        val b2 = branches.create(Uuid.random(), tenantId, "Branch 2")
        val i2 = ingredients.create(Uuid.random(), tenantId, "Salt", null)
        val i3 = ingredients.create(Uuid.random(), tenantId, "Yeast", null)

        val allSeqsAscending = listOf(b1.serverSeq, i1.serverSeq, b2.serverSeq, i2.serverSeq, i3.serverSeq)
        assertEquals(allSeqsAscending.sorted(), allSeqsAscending)

        val firstPage = pull.pull(tenantId, since = 0, limit = 2, entityTypes = null)
        assertEquals(2, firstPage.rows.size)
        assertTrue(firstPage.hasMore)
        assertEquals(listOf(b1.serverSeq, i1.serverSeq), firstPage.rows.map { it.serverSeq })

        val secondPage = pull.pull(tenantId, since = firstPage.rows.last().serverSeq, limit = 2, entityTypes = null)
        assertEquals(2, secondPage.rows.size)
        assertTrue(secondPage.hasMore)
        assertEquals(listOf(b2.serverSeq, i2.serverSeq), secondPage.rows.map { it.serverSeq })

        val thirdPage = pull.pull(tenantId, since = secondPage.rows.last().serverSeq, limit = 2, entityTypes = null)
        assertEquals(1, thirdPage.rows.size)
        assertFalse(thirdPage.hasMore)
        assertEquals(i3.serverSeq, thirdPage.rows.single().serverSeq)
    }

    @Test
    fun `entityTypes filter restricts the merge to the requested tables`() = runTest {
        val tenantId = Uuid.random()
        tenants.create(tenantId, "reference", "Reference Bakery", "CLP")
        branches.create(Uuid.random(), tenantId, "Branch 1")
        ingredients.create(Uuid.random(), tenantId, "Flour", null)

        val page = pull.pull(tenantId, since = 0, limit = 10, entityTypes = setOf(SyncEntityType.INGREDIENT))
        assertEquals(1, page.rows.size)
        assertEquals(SyncEntityType.INGREDIENT.name, page.rows.single().entityType)
    }

    @Test
    fun `an Almacen product is re-pulled, with its origin, whenever its stock moves`() = runTest {
        val tenantId = Uuid.random()
        tenants.create(tenantId, "reference", "Reference Bakery", "CLP")
        val branch = branches.create(Uuid.random(), tenantId, "Main")
        val cashier = users.create(Uuid.random(), tenantId, branch.id, "c1", "hash", null, "Cashier", Role.CASHIER)
        val cola = catalog.createProduct(Uuid.random(), tenantId, "Cola", "Bebidas", SoldBy.UNIT, null, 1500, null, origin = ProductOrigin.ALMACEN)
        val bread = catalog.createProduct(Uuid.random(), tenantId, "Marraqueta", "Pan", SoldBy.UNIT, null, 300, null)

        suspend fun pulledProducts(since: Long) = pull.pull(tenantId, since, 50, setOf(SyncEntityType.PRODUCT)).rows.map { it.data as Product }

        val initial = pulledProducts(0)
        assertEquals(ProductOrigin.ALMACEN, initial.single { it.id == cola.id }.origin)
        val cursor = initial.maxOf { it.serverSeq }
        assertEquals(emptyList(), pulledProducts(cursor))

        productStock.append(Uuid.random(), tenantId, branch.id, cola.id, 12, ProductStockMovementKind.PURCHASE, null, null, Clock.System.now())
        assertEquals(listOf(cola.id), pulledProducts(cursor).map { it.id })

        val afterMovement = pulledProducts(0).single { it.id == cola.id }.serverSeq
        sales.createWithLines(
            Uuid.random(), tenantId, branch.id, cashier.id, Clock.System.now(), PaymentMethod.CASH, 1500, "till-1",
            listOf(SaleLineInput(Uuid.random(), cola.id, SoldBy.UNIT, null, 1, 1500, 1500))
        )
        assertEquals(listOf(cola.id), pulledProducts(afterMovement).map { it.id })

        // A Panadería product's sale or movement never churns its row.
        val afterSale = pulledProducts(0).single { it.id == cola.id }.serverSeq
        sales.createWithLines(
            Uuid.random(), tenantId, branch.id, cashier.id, Clock.System.now(), PaymentMethod.CASH, 300, "till-1",
            listOf(SaleLineInput(Uuid.random(), bread.id, SoldBy.UNIT, null, 1, 300, 300))
        )
        assertEquals(emptyList(), pulledProducts(afterSale))
    }

    @Test
    fun `forecast rows are branch-scoped and re-stamped by every upsert`() = runTest {
        val tenantId = Uuid.random()
        tenants.create(tenantId, "reference", "Reference Bakery", "CLP")
        val here = branches.create(Uuid.random(), tenantId, "Here")
        val there = branches.create(Uuid.random(), tenantId, "There")
        val bread = catalog.createProduct(Uuid.random(), tenantId, "Marraqueta", "Pan", SoldBy.WEIGHT, 1000, null, null)
        val forecasts = com.f776.ecopan.data.database.repository.ForecastRepositoryImpl()
        val day = kotlinx.datetime.LocalDate(2026, 7, 30)

        val first = forecasts.upsert(tenantId, here.id, bread.id, day, 10_000, null, "baseline-swlw")
        forecasts.upsert(tenantId, there.id, bread.id, day, 1, null, "baseline-swlw")
        val only = setOf(SyncEntityType.FORECAST)

        val mine = pull.pull(tenantId, 0, 50, only, branchId = here.id).rows
        assertEquals(listOf(first.id), mine.map { (it.data as com.f776.ecopan.data.database.repository.model.Forecast).id })
        assertEquals(2, pull.pull(tenantId, 0, 50, only, branchId = null).rows.size)

        // A refresh of the same (branch, product, day) moves the row past the cursor.
        forecasts.upsert(tenantId, here.id, bread.id, day, 12_000, null, "baseline-swlw")
        val again = pull.pull(tenantId, mine.single().serverSeq, 50, only, branchId = here.id).rows.single()
        assertEquals(12_000L, (again.data as com.f776.ecopan.data.database.repository.model.Forecast).predictedGrams)
    }
}
