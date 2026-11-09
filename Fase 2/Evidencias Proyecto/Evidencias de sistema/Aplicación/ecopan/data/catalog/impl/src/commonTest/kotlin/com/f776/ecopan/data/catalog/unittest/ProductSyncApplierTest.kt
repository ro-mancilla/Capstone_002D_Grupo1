package com.f776.ecopan.data.catalog.unittest

import com.f776.ecopan.core.domain.catalog.ProductOrigin
import com.f776.ecopan.core.domain.tenant.EntitlementModule
import com.f776.ecopan.data.catalog.ProductRepositoryImpl
import com.f776.ecopan.data.catalog.sync.ProductSyncApplier
import com.f776.ecopan.data.catalog.unittest.fake.FakeProductDao
import com.f776.ecopan.data.catalog.unittest.fake.FakeSaleDao
import com.f776.ecopan.data.catalog.unittest.fake.FakeTenantRepository
import com.f776.ecopan.domain.sync.SyncOpType
import com.f776.ecopan.domain.tenant.TenantProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class ProductSyncApplierTest {

    private val tenantRepository = FakeTenantRepository()
    private val repository = ProductRepositoryImpl(FakeProductDao(), FakeSaleDao(), tenantRepository)
    private val applier = ProductSyncApplier(repository, tenantRepository)

    private suspend fun pull(json: String) {
        tenantRepository.upsertTenant(TenantProfile(Uuid.random(), "t", "T", "CLP", setOf(EntitlementModule.POS_SALES)))
        applier.apply(Json.parseToJsonElement(json), SyncOpType.UPSERT, serverSeq = 7)
    }

    @Test
    fun `an Almacen payload carries its stock and reorder level into the cache`() = runTest {
        pull(
            """{"id":"${Uuid.random()}","name":"Cola","productType":"Bebidas","soldBy":"UNIT","priceMinor":1500,
               "active":true,"origin":"ALMACEN","lowStockThresholdUnits":5,"stockUnits":-2}""",
        )

        val cola = repository.observeActiveProducts().first().single()
        assertEquals(ProductOrigin.ALMACEN, cola.origin)
        assertEquals(5L, cola.lowStockThresholdUnits)
        assertEquals(-2L, cola.stockUnits)
    }

    @Test
    fun `a payload from a server without stock fields leaves them unknown`() = runTest {
        pull("""{"id":"${Uuid.random()}","name":"Cola","productType":"Bebidas","soldBy":"UNIT","priceMinor":1500,"origin":"ALMACEN"}""")

        val cola = repository.observeActiveProducts().first().single()
        assertNull(cola.stockUnits)
        assertNull(cola.lowStockThresholdUnits)
    }
}
