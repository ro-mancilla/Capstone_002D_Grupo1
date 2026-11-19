package com.f776.unigrid.core.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.f776.ecopan.core.domain.catalog.ProductOrigin
import com.f776.ecopan.core.domain.catalog.SoldBy
import com.f776.unigrid.core.database.entity.FormulaEntity
import com.f776.unigrid.core.database.entity.FormulaLineEntity
import com.f776.unigrid.core.database.entity.IngredientEntity
import com.f776.unigrid.core.database.entity.ProductEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Schema smoke tests for the catalog config cache tables added in [MIGRATION_3_4]. */
class CatalogSchemaTest {

    private val database = Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .build()

    private val now: Instant = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds())
    private val tenantId = Uuid.random()

    @AfterTest
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `products round-trip soldBy and only active ones are observed, ordered by name`() = runTest {
        val dao = database.productDao()
        val weightProduct = product(name = "Marraqueta", soldBy = SoldBy.WEIGHT, pricePerKgMinor = 1899)
        val unitProduct = product(name = "Empanada", soldBy = SoldBy.UNIT, priceMinor = 900)
        val inactiveProduct = product(name = "Discontinued", soldBy = SoldBy.UNIT, priceMinor = 100, active = false)
        dao.upsertAll(listOf(weightProduct, unitProduct, inactiveProduct))

        val active = dao.observeActiveByTenant(tenantId).first()

        assertEquals(listOf("Empanada", "Marraqueta"), active.map { it.name })
        assertEquals(SoldBy.WEIGHT, active.single { it.name == "Marraqueta" }.soldBy)
    }

    @Test
    fun `products round-trip origin and default to Panaderia`() = runTest {
        val dao = database.productDao()
        val baked = product(name = "Marraqueta", soldBy = SoldBy.WEIGHT, pricePerKgMinor = 1899)
        val bought = product(name = "Bebida", soldBy = SoldBy.UNIT, priceMinor = 1500).copy(origin = ProductOrigin.ALMACEN)
        dao.upsertAll(listOf(baked, bought))

        val active = dao.observeActiveByTenant(tenantId).first()

        assertEquals(ProductOrigin.PANADERIA, active.single { it.name == "Marraqueta" }.origin)
        assertEquals(ProductOrigin.ALMACEN, active.single { it.name == "Bebida" }.origin)
    }

    @Test
    fun `products round-trip Almacen stock and reorder level, null by default`() = runTest {
        val dao = database.productDao()
        val bought = product(name = "Bebida", soldBy = SoldBy.UNIT, priceMinor = 1500)
            .copy(origin = ProductOrigin.ALMACEN, lowStockThresholdUnits = 5, stockUnits = -2)
        val baked = product(name = "Marraqueta", soldBy = SoldBy.WEIGHT, pricePerKgMinor = 1899)
        dao.upsertAll(listOf(bought, baked))

        val active = dao.observeActiveByTenant(tenantId).first()

        assertEquals(5L, active.single { it.name == "Bebida" }.lowStockThresholdUnits)
        assertEquals(-2L, active.single { it.name == "Bebida" }.stockUnits)
        assertNull(active.single { it.name == "Marraqueta" }.stockUnits)
    }

    @Test
    fun `a product is findable by barcode, but not once inactive`() = runTest {
        val dao = database.productDao()
        val barcoded = product(name = "Bottled drink", soldBy = SoldBy.UNIT, priceMinor = 1200, barcode = "7801234567890")
        dao.upsertAll(listOf(barcoded))

        assertEquals(barcoded, dao.findByBarcode(tenantId, "7801234567890"))

        dao.upsertAll(listOf(barcoded.copy(active = false)))
        assertNull(dao.findByBarcode(tenantId, "7801234567890"))
    }

    @Test
    fun `replaceFormula wholesale-replaces lines in one transaction`() = runTest {
        val dao = database.formulaDao()
        val productId = Uuid.random()
        val flourId = Uuid.random()
        val waterId = Uuid.random()
        val saltId = Uuid.random()
        val formula = FormulaEntity(Uuid.random(), tenantId, productId, active = true, serverSeq = 1, cachedAt = now)

        dao.replaceFormula(
            formula,
            listOf(
                FormulaLineEntity(Uuid.random(), tenantId, formula.id, flourId, 10_000),
                FormulaLineEntity(Uuid.random(), tenantId, formula.id, waterId, 6_500),
            )
        )
        assertEquals(setOf(flourId, waterId), dao.observeLines(formula.id).first().map { it.ingredientId }.toSet())

        // A later PUT drops water, adds salt — the old water line must not linger.
        dao.replaceFormula(
            formula.copy(serverSeq = 2),
            listOf(
                FormulaLineEntity(Uuid.random(), tenantId, formula.id, flourId, 10_000),
                FormulaLineEntity(Uuid.random(), tenantId, formula.id, saltId, 200),
            )
        )

        val lines = dao.observeLines(formula.id).first()
        assertEquals(setOf(flourId, saltId), lines.map { it.ingredientId }.toSet())
        assertEquals(2L, dao.observeByProduct(productId).first()?.serverSeq)
    }

    @Test
    fun `ingredients round-trip a nullable low-stock threshold`() = runTest {
        val dao = database.ingredientDao()
        dao.upsertAll(
            listOf(
                IngredientEntity(Uuid.random(), tenantId, "Flour", lowStockThresholdGrams = 5_000, active = true, serverSeq = 1, cachedAt = now),
                IngredientEntity(Uuid.random(), tenantId, "Yeast", lowStockThresholdGrams = null, active = true, serverSeq = 1, cachedAt = now),
            )
        )

        val ingredients = dao.observeActiveByTenant(tenantId).first().associateBy { it.name }

        assertEquals(5_000L, ingredients.getValue("Flour").lowStockThresholdGrams)
        assertNull(ingredients.getValue("Yeast").lowStockThresholdGrams)
    }

    private fun product(
        name: String,
        soldBy: SoldBy,
        pricePerKgMinor: Long? = null,
        priceMinor: Long? = null,
        barcode: String? = null,
        active: Boolean = true,
    ) = ProductEntity(
        id = Uuid.random(),
        tenantId = tenantId,
        name = name,
        productType = name.lowercase(),
        soldBy = soldBy,
        pricePerKgMinor = pricePerKgMinor,
        priceMinor = priceMinor,
        barcode = barcode,
        imageUrl = null,
        active = active,
        serverSeq = 1,
        cachedAt = now,
        mixToLoafRatioMilli = null,
        minMixGrams = null,
        maxMixGrams = null,
    )
}
