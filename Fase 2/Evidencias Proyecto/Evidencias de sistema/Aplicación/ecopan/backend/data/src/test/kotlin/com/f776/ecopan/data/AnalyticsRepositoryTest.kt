package com.f776.ecopan.data

import com.f776.ecopan.data.database.entity.PaymentMethod
import com.f776.ecopan.data.database.entity.SoldBy
import com.f776.ecopan.data.database.repository.AnalyticsRepositoryImpl
import com.f776.ecopan.data.database.repository.BranchRepositoryImpl
import com.f776.ecopan.data.database.repository.CatalogRepositoryImpl
import com.f776.ecopan.data.database.repository.ProductionBatchRepositoryImpl
import com.f776.ecopan.data.database.repository.SaleRepositoryImpl
import com.f776.ecopan.data.database.repository.TenantRepositoryImpl
import com.f776.ecopan.data.database.repository.UserRepositoryImpl
import com.f776.ecopan.data.database.repository.model.SaleLineInput
import com.f776.ecopan.data.domain.Role
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** 02 §7/§8 AC: aggregate correctness against a small fixed fixture tenant (known totals). */
class AnalyticsRepositoryTest : RepositoryTestBase() {

    private val tenants = TenantRepositoryImpl()
    private val branches = BranchRepositoryImpl()
    private val users = UserRepositoryImpl()
    private val catalog = CatalogRepositoryImpl()
    private val sales = SaleRepositoryImpl(Clock.System)
    private val batches = ProductionBatchRepositoryImpl()
    private val analytics = AnalyticsRepositoryImpl()

    @BeforeEach
    fun setUp() = truncateAll()

    @Test
    fun `saleLineFacts sums to the fixture's known totals, scoped to tenant, branch and range`() = runTest {
        val tenantId = Uuid.random()
        tenants.create(tenantId, "reference", "Reference Bakery", "CLP")
        val branch = branches.create(Uuid.random(), tenantId, "Main")
        val otherBranch = branches.create(Uuid.random(), tenantId, "Other")
        val cashier = users.create(Uuid.random(), tenantId, branch.id, "cashier1", "hash", null, "Cashier One", Role.CASHIER)
        val bread = catalog.createProduct(Uuid.random(), tenantId, "Marraqueta", "BREAD", SoldBy.WEIGHT, 2500, null, null)
        val pastry = catalog.createProduct(Uuid.random(), tenantId, "Empanada", "PASTRY", SoldBy.UNIT, null, 1200, null)

        val now = Clock.System.now()

        // Two sales in range, in the target branch: 500g bread @2500/kg = 1250; 3 pastries @1200 = 3600.
        sales.createWithLines(
            Uuid.random(), tenantId, branch.id, cashier.id, now, PaymentMethod.CASH, 1250, "pos-1",
            listOf(SaleLineInput(Uuid.random(), bread.id, SoldBy.WEIGHT, 500, null, 2500, 1250))
        )
        sales.createWithLines(
            Uuid.random(), tenantId, branch.id, cashier.id, now, PaymentMethod.CARD, 3600, "pos-1",
            listOf(SaleLineInput(Uuid.random(), pastry.id, SoldBy.UNIT, null, 3, 1200, 3600))
        )
        // One sale in a *different* branch -- must not be counted when filtering by [branch.id].
        sales.createWithLines(
            Uuid.random(), tenantId, otherBranch.id, cashier.id, now, PaymentMethod.CASH, 9999, "pos-2",
            listOf(SaleLineInput(Uuid.random(), bread.id, SoldBy.WEIGHT, 1000, null, 2500, 9999))
        )
        // One sale far outside the query range -- must not be counted.
        sales.createWithLines(
            Uuid.random(), tenantId, branch.id, cashier.id, now - 100.hours, PaymentMethod.CASH, 5000, "pos-1",
            listOf(SaleLineInput(Uuid.random(), bread.id, SoldBy.WEIGHT, 2000, null, 2500, 5000))
        )

        val facts = analytics.saleLineFacts(tenantId, branch.id, now - 1.hours, now + 1.hours)

        assertEquals(2, facts.size)
        assertEquals(4850L, facts.sumOf { it.lineTotalMinor })
        assertEquals(500L, facts.filter { it.productType == "BREAD" }.sumOf { it.weightGrams ?: 0L })
        assertEquals(3L, facts.filter { it.productType == "PASTRY" }.sumOf { (it.quantity ?: 0).toLong() })
    }

    @Test
    fun `productionFacts sums mix weight per known fixture batches`() = runTest {
        val tenantId = Uuid.random()
        tenants.create(tenantId, "reference", "Reference Bakery", "CLP")
        val branch = branches.create(Uuid.random(), tenantId, "Main")
        val baker = users.create(Uuid.random(), tenantId, branch.id, "baker1", "hash", null, "Baker One", Role.BAKER)
        val product = catalog.createProduct(Uuid.random(), tenantId, "Marraqueta", "BREAD", SoldBy.WEIGHT, 2500, null, null)

        val now = Clock.System.now()
        batches.create(Uuid.random(), tenantId, branch.id, baker.id, product.id, 10_000, now, true, "baker-1")
        batches.create(Uuid.random(), tenantId, branch.id, baker.id, product.id, 8_000, now, true, "baker-1")

        val facts = analytics.productionFacts(tenantId, branch.id, now - 1.hours, now + 1.hours)

        assertEquals(2, facts.size)
        assertEquals(18_000L, facts.sumOf { it.mixWeightGrams })
        assertEquals(setOf("BREAD"), facts.map { it.productType }.toSet())
        assertEquals(setOf(product.id), facts.map { it.productId }.toSet())
        assertEquals(setOf("Marraqueta"), facts.map { it.productName }.toSet())
    }

    @Test
    fun `productionFacts never returns another tenant's batches`() = runTest {
        val mine = Uuid.random()
        val theirs = Uuid.random()
        tenants.create(mine, "mine", "Mine", "CLP")
        tenants.create(theirs, "theirs", "Theirs", "CLP")
        val myBranch = branches.create(Uuid.random(), mine, "Main")
        val theirBranch = branches.create(Uuid.random(), theirs, "Main")
        val myBaker = users.create(Uuid.random(), mine, myBranch.id, "baker-a", "hash", null, "Baker A", Role.BAKER)
        val theirBaker = users.create(Uuid.random(), theirs, theirBranch.id, "baker-b", "hash", null, "Baker B", Role.BAKER)
        val myProduct = catalog.createProduct(Uuid.random(), mine, "Marraqueta", "BREAD", SoldBy.WEIGHT, 2500, null, null)
        val theirProduct = catalog.createProduct(Uuid.random(), theirs, "Hallulla", "BREAD", SoldBy.WEIGHT, 2500, null, null)

        val now = Clock.System.now()
        batches.create(Uuid.random(), mine, myBranch.id, myBaker.id, myProduct.id, 10_000, now, true, "a-1")
        batches.create(Uuid.random(), theirs, theirBranch.id, theirBaker.id, theirProduct.id, 99_000, now, true, "b-1")

        val facts = analytics.productionFacts(mine, null, now - 1.hours, now + 1.hours)

        assertEquals(listOf(myProduct.id), facts.map { it.productId })
        assertEquals(10_000L, facts.sumOf { it.mixWeightGrams })
    }

    @Test
    fun `productionFacts includes a batch exactly at the range bounds and excludes one just outside`() = runTest {
        val tenantId = Uuid.random()
        tenants.create(tenantId, "reference", "Reference Bakery", "CLP")
        val branch = branches.create(Uuid.random(), tenantId, "Main")
        val baker = users.create(Uuid.random(), tenantId, branch.id, "baker1", "hash", null, "Baker One", Role.BAKER)
        val product = catalog.createProduct(Uuid.random(), tenantId, "Marraqueta", "BREAD", SoldBy.WEIGHT, 2500, null, null)

        val from = Instant.parse("2026-01-10T03:00:00Z")
        val to = Instant.parse("2026-01-11T02:59:59.999Z")
        batches.create(Uuid.random(), tenantId, branch.id, baker.id, product.id, 1_000, from, true, "k-1")
        batches.create(Uuid.random(), tenantId, branch.id, baker.id, product.id, 2_000, to, true, "k-2")
        batches.create(Uuid.random(), tenantId, branch.id, baker.id, product.id, 4_000, from - 1.milliseconds, true, "k-3")
        batches.create(Uuid.random(), tenantId, branch.id, baker.id, product.id, 8_000, to + 1.milliseconds, true, "k-4")

        val facts = analytics.productionFacts(tenantId, branch.id, from, to)

        assertEquals(3_000L, facts.sumOf { it.mixWeightGrams })
    }
}
