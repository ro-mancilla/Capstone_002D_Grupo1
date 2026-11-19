package com.f776.ecopan.notifier.domain

import com.f776.ecopan.data.database.entity.DecrementStatus
import com.f776.ecopan.data.database.entity.ProductOrigin
import com.f776.ecopan.data.database.entity.SoldBy
import com.f776.ecopan.data.database.repository.CatalogRepository
import com.f776.ecopan.data.database.repository.OpeningCount
import com.f776.ecopan.data.database.repository.ProductionBatchRepository
import com.f776.ecopan.data.database.repository.model.Formula
import com.f776.ecopan.data.database.repository.model.FormulaLine
import com.f776.ecopan.data.database.repository.model.IngestResult
import com.f776.ecopan.data.database.repository.model.Product
import com.f776.ecopan.data.database.repository.model.ProductionBatch
import com.f776.ecopan.data.messagebroker.SsePushBroker
import com.f776.ecopan.data.messagebroker.model.BatchProcessed
import com.f776.ecopan.data.messagebroker.model.SsePushEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

private class FakeProductionBatchRepository(private val batch: ProductionBatch?) : ProductionBatchRepository {
    override suspend fun create(
        id: Uuid, tenantId: Uuid, branchId: Uuid, bakerId: Uuid, productId: Uuid, mixWeightGrams: Long,
        batchAt: Instant, movementsMinted: Boolean, deviceId: String
    ): IngestResult<ProductionBatch> = throw NotImplementedError()

    override suspend fun findById(id: Uuid): ProductionBatch? = batch?.takeIf { it.id == id }
    override suspend fun listSince(tenantId: Uuid, since: Long, limit: Int): List<ProductionBatch> = emptyList()
    override suspend fun listReceivedSince(tenantId: Uuid, since: Instant): List<ProductionBatch> = emptyList()
    override suspend fun updateDecrementStatus(id: Uuid, status: DecrementStatus): ProductionBatch? = null
    override suspend fun completeDecrement(
        id: Uuid,
        status: DecrementStatus,
        movements: List<com.f776.ecopan.data.database.repository.model.DecrementMovement>,
    ): Boolean = false
}

private class FakeCatalogRepository(private val product: Product?) : CatalogRepository {
    override suspend fun createProduct(
        id: Uuid, tenantId: Uuid, name: String, productType: String, soldBy: SoldBy,
        pricePerKgMinor: Long?, priceMinor: Long?, barcode: String?, imageUrl: String?, origin: ProductOrigin, opening: OpeningCount?
    ): Product = throw NotImplementedError()

    override suspend fun findProductById(id: Uuid): Product? = product?.takeIf { it.id == id }
    override suspend fun listProducts(tenantId: Uuid, includeInactive: Boolean): List<Product> = emptyList()
    override suspend fun updateProduct(
        id: Uuid, name: String?, productType: String?, soldBy: SoldBy?, pricePerKgMinor: Long?,
        priceMinor: Long?, barcode: String?, imageUrl: String?, active: Boolean?, origin: ProductOrigin?
    ): Product? = null

    override suspend fun setProductImageUrl(id: Uuid, imageUrl: String?): Product? = null

    override suspend fun setProductLowStockThreshold(id: Uuid, thresholdUnits: Long?): Product? = null

    override suspend fun findFormulaByProduct(tenantId: Uuid, productId: Uuid): Formula? = null
    override suspend fun listFormulas(tenantId: Uuid, includeInactive: Boolean): List<Formula> = emptyList()
    override suspend fun putFormula(id: Uuid, tenantId: Uuid, productId: Uuid, lines: List<FormulaLine>): Formula =
        throw NotImplementedError()
}

private class FakeSsePushBroker : SsePushBroker {
    val published = mutableListOf<SsePushEvent>()
    override suspend fun publish(event: SsePushEvent) {
        published += event
    }
    override suspend fun consume(block: suspend (SsePushEvent) -> Unit) {}
}

class NotificationProcessorImplTest {

    @Test
    fun `BatchProcessed publishes exactly one batch-ready SsePushEvent with tenant-branch keying`() = runTest {
        val tenantId = Uuid.random()
        val branchId = Uuid.random()
        val productId = Uuid.random()
        val batchId = Uuid.random()

        val batch = ProductionBatch(
            id = batchId, tenantId = tenantId, branchId = branchId, bakerId = Uuid.random(), productId = productId,
            mixWeightGrams = 10_000, batchAt = Clock.System.now(), movementsMinted = true,
            decrementStatus = DecrementStatus.APPLIED, deviceId = "device-1", serverSeq = 1, receivedAt = Clock.System.now()
        )
        val product = Product(
            id = productId, tenantId = tenantId, name = "Marraqueta", productType = "BREAD", soldBy = SoldBy.WEIGHT,
            pricePerKgMinor = 2500, priceMinor = null, barcode = null, imageUrl = null, active = true, serverSeq = 1,
            createdAt = Clock.System.now(), updatedAt = Clock.System.now()
        )

        val broker = FakeSsePushBroker()
        val sut = NotificationProcessorImpl(FakeProductionBatchRepository(batch), FakeCatalogRepository(product), broker)

        sut.handle(BatchProcessed(batchId = batchId.toString(), tenantId = tenantId.toString(), branchId = branchId.toString(), status = "APPLIED"))

        assertEquals(1, broker.published.size)
        val event = broker.published.single()
        assertEquals(tenantId.toString(), event.tenantId)
        assertEquals(branchId.toString(), event.branchId)
        assertEquals("batch-ready", event.type)
    }

    @Test
    fun `unknown batch is dropped without publishing`() = runTest {
        val broker = FakeSsePushBroker()
        val sut = NotificationProcessorImpl(FakeProductionBatchRepository(null), FakeCatalogRepository(null), broker)

        sut.handle(BatchProcessed(batchId = Uuid.random().toString(), tenantId = Uuid.random().toString(), branchId = Uuid.random().toString(), status = "APPLIED"))

        assertEquals(0, broker.published.size)
    }
}
