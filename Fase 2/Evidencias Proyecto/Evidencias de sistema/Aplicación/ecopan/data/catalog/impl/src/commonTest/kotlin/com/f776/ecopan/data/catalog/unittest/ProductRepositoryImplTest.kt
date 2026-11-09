package com.f776.ecopan.data.catalog.unittest

import com.f776.ecopan.core.domain.catalog.SoldBy
import com.f776.ecopan.core.domain.tenant.EntitlementModule
import com.f776.ecopan.data.catalog.ProductRepositoryImpl
import com.f776.ecopan.data.catalog.unittest.fake.FakeProductDao
import com.f776.ecopan.data.catalog.unittest.fake.FakeSaleDao
import com.f776.ecopan.core.domain.catalog.ProductOrigin
import com.f776.unigrid.core.database.dao.UnsyncedProductUnits
import com.f776.ecopan.data.catalog.unittest.fake.FakeTenantRepository
import com.f776.ecopan.domain.catalog.ProductUpsert
import com.f776.ecopan.domain.tenant.TenantProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class ProductRepositoryImplTest {

    private val tenantId = Uuid.random()
    private val tenantRepository = FakeTenantRepository()
    private val saleDao = FakeSaleDao()
    private val repository = ProductRepositoryImpl(FakeProductDao(), saleDao, tenantRepository)

    @Test
    fun `no tenant cached yet observes an empty product list`() = runTest {
        assertEquals(emptyList(), repository.observeActiveProducts().first())
    }

    @Test
    fun `active products are observed ordered by name inactive ones are excluded`() = runTest {
        cacheTenant()
        repository.upsertProducts(
            listOf(
                product("Marraqueta", SoldBy.WEIGHT, pricePerKgMinor = 1899),
                product("Empanada", SoldBy.UNIT, priceMinor = 900),
                product("Discontinued", SoldBy.UNIT, priceMinor = 100, active = false),
            )
        )

        val names = repository.observeActiveProducts().first().map { it.name }

        assertEquals(listOf("Empanada", "Marraqueta"), names)
    }

    @Test
    fun `a product is findable by barcode until it goes inactive`() = runTest {
        cacheTenant()
        val barcoded = product("Bottled drink", SoldBy.UNIT, priceMinor = 1200, barcode = "7801234567890")
        repository.upsertProducts(listOf(barcoded))

        assertEquals("Bottled drink", repository.findByBarcode("7801234567890")?.name)

        repository.upsertProducts(listOf(barcoded.copy(active = false)))
        assertNull(repository.findByBarcode("7801234567890"))
    }

    @Test
    fun `findByBarcode with no tenant cached returns null instead of throwing`() = runTest {
        assertNull(repository.findByBarcode("anything"))
    }

    @Test
    fun `Almacen stock is the pulled figure less units sold here and not yet synced`() = runTest {
        cacheTenant()
        val cola = product("Cola", SoldBy.UNIT, priceMinor = 1500).copy(
            origin = ProductOrigin.ALMACEN, lowStockThresholdUnits = 5, stockUnits = 10,
        )
        val untouched = product("Agua", SoldBy.UNIT, priceMinor = 800).copy(origin = ProductOrigin.ALMACEN, stockUnits = 4)
        val noInfo = product("Galleta", SoldBy.UNIT, priceMinor = 700).copy(origin = ProductOrigin.ALMACEN)
        repository.upsertProducts(listOf(cola, untouched, noInfo))
        saleDao.unsynced.value = listOf(UnsyncedProductUnits(cola.id, 3), UnsyncedProductUnits(noInfo.id, 2))

        val byName = repository.observeActiveProducts().first().associateBy { it.name }

        assertEquals(7L, byName.getValue("Cola").stockUnits)
        assertEquals(5L, byName.getValue("Cola").lowStockThresholdUnits)
        assertEquals(4L, byName.getValue("Agua").stockUnits)
        // No stock figure from the server stays "unknown", however much was sold locally.
        assertNull(byName.getValue("Galleta").stockUnits)

        // Selling past zero just goes negative; the figure is never clamped.
        saleDao.unsynced.value = listOf(UnsyncedProductUnits(cola.id, 14))
        assertEquals(-4L, repository.observeActiveProducts().first().single { it.name == "Cola" }.stockUnits)
    }

    private suspend fun cacheTenant() {
        tenantRepository.upsertTenant(
            TenantProfile(tenantId, "panaderia-centro", "Panadería Centro", "CLP", setOf(EntitlementModule.POS_SALES))
        )
    }

    private fun product(
        name: String,
        soldBy: SoldBy,
        pricePerKgMinor: Long? = null,
        priceMinor: Long? = null,
        barcode: String? = null,
        active: Boolean = true,
    ) = ProductUpsert(
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
        mixToLoafRatioMilli = null,
        minMixGrams = null,
        maxMixGrams = null,
    )
}
