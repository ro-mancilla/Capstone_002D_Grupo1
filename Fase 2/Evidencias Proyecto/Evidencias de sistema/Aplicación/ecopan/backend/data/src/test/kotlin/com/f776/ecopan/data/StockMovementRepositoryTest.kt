package com.f776.ecopan.data

import com.f776.ecopan.data.database.entity.SoldBy
import com.f776.ecopan.data.database.entity.StockMovementKind
import com.f776.ecopan.data.database.repository.BranchRepositoryImpl
import com.f776.ecopan.data.database.repository.CatalogRepositoryImpl
import com.f776.ecopan.data.database.repository.IngredientRepositoryImpl
import com.f776.ecopan.data.database.repository.ProductionBatchRepositoryImpl
import com.f776.ecopan.data.database.repository.StockMovementRepositoryImpl
import com.f776.ecopan.data.database.repository.TenantRepositoryImpl
import com.f776.ecopan.data.database.repository.UserRepositoryImpl
import com.f776.ecopan.data.database.repository.model.IngestResult
import com.f776.ecopan.data.domain.Role
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** 02 §8 AC: stock Σ with negative result; decrement movement unique (sourceBatchId, ingredientId) conflict ignored. */
class StockMovementRepositoryTest : RepositoryTestBase() {

    private val tenants = TenantRepositoryImpl()
    private val branches = BranchRepositoryImpl()
    private val users = UserRepositoryImpl()
    private val catalog = CatalogRepositoryImpl()
    private val ingredients = IngredientRepositoryImpl()
    private val productionBatches = ProductionBatchRepositoryImpl()
    private val movements = StockMovementRepositoryImpl()

    @BeforeEach
    fun setUp() = truncateAll()

    @Test
    fun `sum by ingredient allows a negative result -- negative stock is surfaced, not rejected`() = runTest {
        val tenantId = Uuid.random()
        tenants.create(tenantId, "reference", "Reference Bakery", "CLP")
        val branch = branches.create(Uuid.random(), tenantId, "Main")
        val baker = users.create(Uuid.random(), tenantId, branch.id, "baker1", "hash", null, "Baker One", Role.BAKER)
        val flour = ingredients.create(Uuid.random(), tenantId, "Flour", null)

        movements.append(Uuid.random(), tenantId, branch.id, flour.id, 1000, StockMovementKind.PURCHASE, null, null, baker.id, Clock.System.now())
        movements.append(Uuid.random(), tenantId, branch.id, flour.id, -2500, StockMovementKind.MANUAL_ADJUSTMENT, null, "spillage", baker.id, Clock.System.now())

        val sums = movements.sumByIngredient(tenantId, branch.id)
        assertEquals(-1500, sums.single { it.ingredientId == flour.id }.currentGrams)
    }

    @Test
    fun `duplicate source_batch_id + ingredient_id decrement conflict is ignored`() = runTest {
        val tenantId = Uuid.random()
        tenants.create(tenantId, "reference", "Reference Bakery", "CLP")
        val branch = branches.create(Uuid.random(), tenantId, "Main")
        val baker = users.create(Uuid.random(), tenantId, branch.id, "baker1", "hash", null, "Baker One", Role.BAKER)
        val product = catalog.createProduct(Uuid.random(), tenantId, "Marraqueta", "BREAD", SoldBy.WEIGHT, 2500, null, null)
        val flour = ingredients.create(Uuid.random(), tenantId, "Flour", null)
        val batch = productionBatches.create(Uuid.random(), tenantId, branch.id, baker.id, product.id, 10_000, Clock.System.now(), true, "baker-1").value

        val first = movements.append(
            Uuid.random(), tenantId, branch.id, flour.id, -8300, StockMovementKind.FORMULA_DECREMENT, batch.id, null, null, Clock.System.now()
        )
        assertIs<IngestResult.Applied<*>>(first)

        // Different movement id, same (sourceBatchId, ingredientId) -- silently ignored (01 §6.1/02 §1).
        val second = movements.append(
            Uuid.random(), tenantId, branch.id, flour.id, -8300, StockMovementKind.FORMULA_DECREMENT, batch.id, null, null, Clock.System.now()
        )
        assertIs<IngestResult.Duplicate<*>>(second)
        assertEquals(first.value.id, second.value.id)

        assertEquals(-8300, movements.sumByIngredient(tenantId, branch.id).single { it.ingredientId == flour.id }.currentGrams)
    }

    @Test
    fun `listSince returns rows ordered by server_seq for the cursor-based pull`() = runTest {
        val tenantId = Uuid.random()
        tenants.create(tenantId, "reference", "Reference Bakery", "CLP")
        val branch = branches.create(Uuid.random(), tenantId, "Main")
        val baker = users.create(Uuid.random(), tenantId, branch.id, "baker1", "hash", null, "Baker One", Role.BAKER)
        val flour = ingredients.create(Uuid.random(), tenantId, "Flour", null)

        val m1 = movements.append(Uuid.random(), tenantId, branch.id, flour.id, 500, StockMovementKind.PURCHASE, null, null, baker.id, Clock.System.now()).value
        val m2 = movements.append(Uuid.random(), tenantId, branch.id, flour.id, 700, StockMovementKind.PURCHASE, null, null, baker.id, Clock.System.now()).value

        val since0 = movements.listSince(tenantId, 0, 100)
        assertEquals(listOf(m1.id, m2.id), since0.map { it.id })

        val sinceFirst = movements.listSince(tenantId, m1.serverSeq, 100)
        assertEquals(listOf(m2.id), sinceFirst.map { it.id })
    }

    @Test
    fun `reconcile splits opening, purchases, decrements and adjustments so opening + movements = closing`() = runTest {
        val tenantId = Uuid.random()
        tenants.create(tenantId, "reference", "Reference Bakery", "CLP")
        val branch = branches.create(Uuid.random(), tenantId, "Main")
        val otherBranch = branches.create(Uuid.random(), tenantId, "Other")
        val baker = users.create(Uuid.random(), tenantId, branch.id, "baker1", "hash", null, "Baker One", Role.BAKER)
        val product = catalog.createProduct(Uuid.random(), tenantId, "Marraqueta", "BREAD", SoldBy.WEIGHT, 2500, null, null)
        val flour = ingredients.create(Uuid.random(), tenantId, "Flour", null)
        val salt = ingredients.create(Uuid.random(), tenantId, "Salt", null)
        val batch1 = productionBatches.create(Uuid.random(), tenantId, branch.id, baker.id, product.id, 10_000, Clock.System.now(), true, "baker-1").value
        val batch2 = productionBatches.create(Uuid.random(), tenantId, branch.id, baker.id, product.id, 10_000, Clock.System.now(), true, "baker-2").value

        val from = Instant.parse("2026-03-10T00:00:00Z")
        val to = Instant.parse("2026-03-20T00:00:00Z")
        fun at(iso: String) = Instant.parse(iso)
        suspend fun add(ingredientId: Uuid, grams: Long, kind: StockMovementKind, whenAt: Instant, batchId: Uuid? = null, branchId: Uuid = branch.id) =
            movements.append(Uuid.random(), tenantId, branchId, ingredientId, grams, kind, batchId, null, baker.id, whenAt)

        // Before the window: 10_000 bought, 3_000 used -> opening 7_000.
        add(flour.id, 10_000, StockMovementKind.PURCHASE, at("2026-03-01T08:00:00Z"))
        add(flour.id, -3_000, StockMovementKind.MANUAL_ADJUSTMENT, at("2026-03-09T23:59:59Z"))
        // Inside the window.
        add(flour.id, 5_000, StockMovementKind.PURCHASE, at("2026-03-10T00:00:00Z"))       // `from` is inclusive
        add(flour.id, -1_200, StockMovementKind.FORMULA_DECREMENT, at("2026-03-12T06:00:00Z"), batch1.id)
        add(flour.id, -800, StockMovementKind.FORMULA_DECREMENT, at("2026-03-13T06:00:00Z"), batch2.id)
        add(flour.id, -250, StockMovementKind.MANUAL_ADJUSTMENT, at("2026-03-15T10:00:00Z"))
        // At/after `to` and in another branch: must not leak in.
        add(flour.id, 9_999, StockMovementKind.PURCHASE, at("2026-03-20T00:00:00Z"))      // `to` is exclusive
        add(flour.id, 4_444, StockMovementKind.PURCHASE, at("2026-03-12T00:00:00Z"), branchId = otherBranch.id)
        // An ingredient whose only activity is after the window still has a zero row only if it had earlier history.
        add(salt.id, 100, StockMovementKind.PURCHASE, at("2026-03-25T00:00:00Z"))

        val rows = movements.reconcileByIngredient(tenantId, branch.id, from, to).associateBy { it.ingredientId }

        val flourRow = rows.getValue(flour.id)
        assertEquals(7_000, flourRow.openingGrams)
        assertEquals(5_000, flourRow.purchasedGrams)
        assertEquals(-2_000, flourRow.decrementedGrams)
        assertEquals(-250, flourRow.adjustedGrams)
        assertEquals(2, flourRow.batchCount)
        assertEquals(9_750, flourRow.closingGrams)
        assertEquals(null, rows[salt.id], "an ingredient with no movement before `to` has nothing to report")
    }

    /** The reporting zone's calendar day `[from, to]` as the `[start, end)` instants the service hands the repository. */
    private fun santiagoWindow(from: String, to: String): Pair<Instant, Instant> {
        val zone = java.time.ZoneId.of("America/Santiago")
        fun startOf(day: java.time.LocalDate) = Instant.fromEpochMilliseconds(day.atStartOfDay(zone).toInstant().toEpochMilli())
        return startOf(java.time.LocalDate.parse(from)) to startOf(java.time.LocalDate.parse(to).plusDays(1))
    }

    @Test
    fun `reporting-day bounds fall on non-UTC-midnight instants and hold in summer, winter and across the DST change`() = runTest {
        val tenantId = Uuid.random()
        tenants.create(tenantId, "reference", "Reference Bakery", "CLP")
        val branch = branches.create(Uuid.random(), tenantId, "Main")
        val flour = ingredients.create(Uuid.random(), tenantId, "Flour", null)
        suspend fun add(grams: Long, at: String) =
            movements.append(Uuid.random(), tenantId, branch.id, flour.id, grams, StockMovementKind.PURCHASE, null, null, null, Instant.parse(at))

        // Summer (UTC-3): local midnight is 03:00Z. Day 10 Mar starts at 03:00Z, day 12 Mar ends at 03:00Z on the 13th.
        add(1, "2026-03-10T02:59:59.999Z") // 9 Mar 23:59:59.999 locally -> opening
        add(10, "2026-03-10T03:00:00Z") // from-inclusive
        add(100, "2026-03-13T02:59:59.999Z") // last millisecond of 12 Mar
        add(1_000, "2026-03-13T03:00:00Z") // to-exclusive: belongs to 13 Mar
        val (summerFrom, summerTo) = santiagoWindow("2026-03-10", "2026-03-12")
        assertEquals(Instant.parse("2026-03-10T03:00:00Z"), summerFrom)
        assertEquals(Instant.parse("2026-03-13T03:00:00Z"), summerTo)
        movements.reconcileByIngredient(tenantId, branch.id, summerFrom, summerTo).single().let {
            assertEquals(1, it.openingGrams)
            assertEquals(110, it.purchasedGrams)
            assertEquals(111, it.closingGrams)
        }

        // Winter (UTC-4): local midnight is 04:00Z, so a 03:30Z movement is still the previous local day.
        add(7, "2026-07-10T03:30:00Z")
        add(70, "2026-07-10T04:00:00Z")
        val (winterFrom, winterTo) = santiagoWindow("2026-07-10", "2026-07-10")
        assertEquals(Instant.parse("2026-07-10T04:00:00Z"), winterFrom)
        assertEquals(Instant.parse("2026-07-11T04:00:00Z"), winterTo)
        movements.reconcileByIngredient(tenantId, branch.id, winterFrom, winterTo).single().let {
            assertEquals(1 + 10 + 100 + 1_000 + 7, it.openingGrams)
            assertEquals(70, it.purchasedGrams)
        }

        // The day the clocks go back is 25 hours long; its bounds still come from the zone rules.
        val (changeFrom, changeTo) = santiagoWindow("2026-04-04", "2026-04-04")
        assertEquals(25, (changeTo - changeFrom).inWholeHours)
        add(5, (changeTo - kotlin.time.Duration.parse("PT0.001S")).toString())
        add(50, changeTo.toString())
        movements.reconcileByIngredient(tenantId, branch.id, changeFrom, changeTo).single().let {
            assertEquals(5, it.purchasedGrams)
        }
    }

    @Test
    fun `an end date of today includes today's movements, and a future-dated movement breaks closing equals stock`() = runTest {
        val tenantId = Uuid.random()
        tenants.create(tenantId, "reference", "Reference Bakery", "CLP")
        val branch = branches.create(Uuid.random(), tenantId, "Main")
        val flour = ingredients.create(Uuid.random(), tenantId, "Flour", null)
        suspend fun add(grams: Long, at: Instant) =
            movements.append(Uuid.random(), tenantId, branch.id, flour.id, grams, StockMovementKind.PURCHASE, null, null, null, at)

        val zone = java.time.ZoneId.of("America/Santiago")
        val today = java.time.LocalDate.now(zone).toString()
        val (from, to) = santiagoWindow(java.time.LocalDate.now(zone).minusDays(7).toString(), today)
        val now = Clock.System.now()

        add(300, now - 2.days)
        add(40, now) // today
        movements.reconcileByIngredient(tenantId, branch.id, from, to).single().let {
            assertEquals(340, it.closingGrams)
            assertEquals(movements.sumByIngredient(tenantId, branch.id).single().currentGrams, it.closingGrams)
        }

        // Dated after the end of today (a clock-skewed device, or a backdated-forward entry): current
        // stock counts it, a report that ends today cannot. Documented limit, pinned here.
        add(9, to + 1.days)
        val closing = movements.reconcileByIngredient(tenantId, branch.id, from, to).single().closingGrams
        assertEquals(340, closing)
        assertEquals(349, movements.sumByIngredient(tenantId, branch.id).single().currentGrams)
    }

    @Test
    fun `every movement kind lands in a bucket, so closing never drifts from the stock sum`() = runTest {
        val tenantId = Uuid.random()
        tenants.create(tenantId, "reference", "Reference Bakery", "CLP")
        val branch = branches.create(Uuid.random(), tenantId, "Main")
        val baker = users.create(Uuid.random(), tenantId, branch.id, "baker1", "hash", null, "Baker One", Role.BAKER)
        val product = catalog.createProduct(Uuid.random(), tenantId, "Marraqueta", "BREAD", SoldBy.WEIGHT, 2500, null, null)
        val flour = ingredients.create(Uuid.random(), tenantId, "Flour", null)
        val batch = productionBatches.create(Uuid.random(), tenantId, branch.id, baker.id, product.id, 10_000, Clock.System.now(), true, "baker-1").value
        val at = Instant.parse("2026-03-11T12:00:00Z")

        // One movement of each kind, whatever kinds exist: a kind added to the enum without a bucket
        // in `reconcileByIngredient` makes the sums below disagree and fails this test.
        StockMovementKind.entries.forEachIndexed { index, kind ->
            val grams = 10L * (index + 1)
            movements.append(
                Uuid.random(), tenantId, branch.id, flour.id, grams, kind,
                if (kind == StockMovementKind.FORMULA_DECREMENT) batch.id else null, null, baker.id, at
            )
        }

        val row = movements.reconcileByIngredient(
            tenantId, branch.id, Instant.parse("2026-03-10T00:00:00Z"), Instant.parse("2026-03-20T00:00:00Z")
        ).single()
        val stock = movements.sumByIngredient(tenantId, branch.id).single().currentGrams
        assertEquals(stock, row.closingGrams)
        assertEquals(stock, row.purchasedGrams + row.decrementedGrams + row.adjustedGrams)
    }

    @Test
    fun `closing balance at an open-ended horizon equals the current stock sum`() = runTest {
        val tenantId = Uuid.random()
        tenants.create(tenantId, "reference", "Reference Bakery", "CLP")
        val branch = branches.create(Uuid.random(), tenantId, "Main")
        val baker = users.create(Uuid.random(), tenantId, branch.id, "baker1", "hash", null, "Baker One", Role.BAKER)
        val flour = ingredients.create(Uuid.random(), tenantId, "Flour", null)
        val now = Clock.System.now()

        movements.append(Uuid.random(), tenantId, branch.id, flour.id, 4_000, StockMovementKind.PURCHASE, null, null, baker.id, now - 3.days)
        movements.append(Uuid.random(), tenantId, branch.id, flour.id, -6_500, StockMovementKind.MANUAL_ADJUSTMENT, null, null, baker.id, now - 1.days)

        val row = movements.reconcileByIngredient(tenantId, branch.id, now - 2.days, now + 1.days).single()
        val current = movements.sumByIngredient(tenantId, branch.id).single()
        assertEquals(current.currentGrams, row.closingGrams)
        assertEquals(-2_500, row.closingGrams)
    }

    @Test
    fun `countDecrementBatches counts a batch once however many ingredients it decrements`() = runTest {
        val tenantId = Uuid.random()
        tenants.create(tenantId, "reference", "Reference Bakery", "CLP")
        val branch = branches.create(Uuid.random(), tenantId, "Main")
        val baker = users.create(Uuid.random(), tenantId, branch.id, "baker1", "hash", null, "Baker One", Role.BAKER)
        val product = catalog.createProduct(Uuid.random(), tenantId, "Marraqueta", "BREAD", SoldBy.WEIGHT, 2500, null, null)
        val ingredientIds = listOf("Flour", "Salt", "Yeast", "Water").map { ingredients.create(Uuid.random(), tenantId, it, null).id }
        val batches = (1..3).map {
            productionBatches.create(Uuid.random(), tenantId, branch.id, baker.id, product.id, 10_000, Clock.System.now(), true, "baker-$it").value
        }
        val from = Instant.parse("2026-03-10T00:00:00Z")
        val to = Instant.parse("2026-03-20T00:00:00Z")

        // 3 batches x 4 ingredients = 12 FORMULA_DECREMENT rows.
        batches.forEach { batch ->
            ingredientIds.forEach { ingredientId ->
                movements.append(
                    Uuid.random(), tenantId, branch.id, ingredientId, -100, StockMovementKind.FORMULA_DECREMENT,
                    batch.id, null, baker.id, Instant.parse("2026-03-12T06:00:00Z")
                )
            }
        }
        // Outside the window and non-decrement rows must not count.
        movements.append(
            Uuid.random(), tenantId, branch.id, ingredientIds.first(), -100, StockMovementKind.FORMULA_DECREMENT,
            batches.first().id, null, baker.id, Instant.parse("2026-03-25T06:00:00Z")
        )
        movements.append(Uuid.random(), tenantId, branch.id, ingredientIds.first(), 500, StockMovementKind.PURCHASE, null, null, baker.id, Instant.parse("2026-03-12T06:00:00Z"))

        assertEquals(3, movements.countDecrementBatches(tenantId, branch.id, from, to))
        assertEquals(12, movements.reconcileByIngredient(tenantId, branch.id, from, to).sumOf { it.batchCount })
        assertEquals(0, movements.countDecrementBatches(tenantId, branch.id, to, to + 1.days))
    }
}
