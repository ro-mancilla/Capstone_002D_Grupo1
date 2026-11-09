package com.f776.ecopan.worker.fakes

import com.f776.ecopan.data.database.entity.DecrementStatus
import com.f776.ecopan.data.database.entity.ProductOrigin
import com.f776.ecopan.data.database.entity.SoldBy
import com.f776.ecopan.data.database.entity.StockMovementKind
import com.f776.ecopan.data.database.repository.AnalyticsRepository
import com.f776.ecopan.data.database.repository.CatalogRepository
import com.f776.ecopan.data.database.repository.OpeningCount
import com.f776.ecopan.data.database.repository.ForecastRepository
import com.f776.ecopan.data.database.repository.ForecastUpsert
import com.f776.ecopan.data.database.repository.ForecastUpsertResult
import com.f776.ecopan.data.database.repository.ProductionBatchRepository
import com.f776.ecopan.data.database.repository.StockMovementRepository
import com.f776.ecopan.data.database.repository.TenantRepository
import com.f776.ecopan.data.database.repository.model.DecrementMovement
import com.f776.ecopan.data.database.repository.model.Forecast
import com.f776.ecopan.data.database.repository.model.Formula
import com.f776.ecopan.data.database.repository.model.FormulaLine
import com.f776.ecopan.data.database.repository.model.IngestResult
import com.f776.ecopan.data.database.repository.model.IngredientReconciliation
import com.f776.ecopan.data.database.repository.model.IngredientStock
import com.f776.ecopan.data.database.repository.model.Product
import com.f776.ecopan.data.database.repository.model.ProductionBatch
import com.f776.ecopan.data.database.repository.model.ProductionFact
import com.f776.ecopan.data.database.repository.model.SaleLineFact
import com.f776.ecopan.data.database.repository.model.StockMovement
import com.f776.ecopan.data.database.repository.model.Tenant
import com.f776.ecopan.data.domain.Module
import com.f776.ecopan.data.messagebroker.BatchEventBroker
import com.f776.ecopan.data.messagebroker.model.BatchProcessed
import com.f776.ecopan.worker.ml.MlForecastClient
import com.f776.ecopan.worker.ml.MlForecastPoint
import com.f776.ecopan.worker.ml.MlForecastRequest
import com.f776.ecopan.worker.ml.MlForecastResponse
import com.f776.ecopan.worker.ml.MlSeriesForecast
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

class FakeProductionBatchRepository(
    /** Where [completeDecrement] books its movements, so tests can read them back. */
    private val movements: FakeStockMovementRepository = FakeStockMovementRepository(),
) : ProductionBatchRepository {
    val batches = mutableMapOf<Uuid, ProductionBatch>()
    private val seq = AtomicLong(0)

    /** The `BatchProcessed` notices [completeDecrement] put on the outbox. */
    val notices = mutableListOf<BatchProcessed>()

    /** Thrown, one per call, by the next [completeDecrement]s -- before anything is written. */
    val completeFailures = ArrayDeque<Exception>()
    var completeCalls = 0
        private set

    fun seed(batch: ProductionBatch) {
        batches[batch.id] = batch
    }

    override suspend fun create(
        id: Uuid, tenantId: Uuid, branchId: Uuid, bakerId: Uuid, productId: Uuid, mixWeightGrams: Long,
        batchAt: Instant, movementsMinted: Boolean, deviceId: String
    ): IngestResult<ProductionBatch> {
        batches[id]?.let { return IngestResult.Duplicate(it) }
        val batch = ProductionBatch(
            id, tenantId, branchId, bakerId, productId, mixWeightGrams, batchAt, movementsMinted,
            DecrementStatus.PENDING, deviceId, seq.incrementAndGet(), Clock.System.now()
        )
        batches[id] = batch
        return IngestResult.Applied(batch)
    }

    override suspend fun findById(id: Uuid): ProductionBatch? = batches[id]

    override suspend fun listSince(tenantId: Uuid, since: Long, limit: Int): List<ProductionBatch> =
        batches.values.filter { it.tenantId == tenantId && it.serverSeq > since }.sortedBy { it.serverSeq }.take(limit)

    override suspend fun listReceivedSince(tenantId: Uuid, since: Instant): List<ProductionBatch> =
        batches.values.filter { it.tenantId == tenantId && it.receivedAt >= since }.sortedBy { it.serverSeq }

    override suspend fun updateDecrementStatus(id: Uuid, status: DecrementStatus): ProductionBatch? {
        val current = batches[id] ?: return null
        val updated = current.copy(decrementStatus = status)
        batches[id] = updated
        return updated
    }

    /** All-or-nothing like the real one: a failure writes nothing, and only PENDING moves. */
    override suspend fun completeDecrement(id: Uuid, status: DecrementStatus, movements: List<DecrementMovement>): Boolean {
        completeCalls += 1
        completeFailures.removeFirstOrNull()?.let { throw it }
        val batch = batches[id]?.takeIf { it.decrementStatus == DecrementStatus.PENDING } ?: return false
        batches[id] = batch.copy(decrementStatus = status)
        movements.forEach { line ->
            this.movements.append(
                Uuid.random(), batch.tenantId, batch.branchId, line.ingredientId, line.deltaGrams,
                StockMovementKind.FORMULA_DECREMENT, batch.id, null, null, batch.batchAt,
            )
        }
        notices += BatchProcessed(batch.id.toString(), batch.tenantId.toString(), batch.branchId.toString(), status.name)
        return true
    }
}

class FakeStockMovementRepository : StockMovementRepository {
    val movements = mutableMapOf<Uuid, StockMovement>()
    private val seq = AtomicLong(0)

    override suspend fun append(
        id: Uuid, tenantId: Uuid, branchId: Uuid, ingredientId: Uuid, deltaGrams: Long, kind: StockMovementKind,
        sourceBatchId: Uuid?, note: String?, createdBy: Uuid?, occurredAt: Instant
    ): IngestResult<StockMovement> {
        movements[id]?.let { return IngestResult.Duplicate(it) }
        if (sourceBatchId != null) {
            movements.values.find { it.sourceBatchId == sourceBatchId && it.ingredientId == ingredientId }?.let {
                return IngestResult.Duplicate(it)
            }
        }
        val movement = StockMovement(
            id, tenantId, branchId, ingredientId, deltaGrams, kind, sourceBatchId, note, createdBy,
            occurredAt, seq.incrementAndGet(), Clock.System.now()
        )
        movements[id] = movement
        return IngestResult.Applied(movement)
    }

    override suspend fun findById(id: Uuid): StockMovement? = movements[id]

    override suspend fun sumByIngredient(tenantId: Uuid, branchId: Uuid): List<IngredientStock> =
        movements.values.filter { it.tenantId == tenantId && it.branchId == branchId }
            .groupBy { it.ingredientId }
            .map { (ingredientId, rows) -> IngredientStock(ingredientId, rows.sumOf { it.deltaGrams }) }

    override suspend fun reconcileByIngredient(tenantId: Uuid, branchId: Uuid, from: Instant, to: Instant): List<IngredientReconciliation> =
        error("not used by the worker")

    override suspend fun countDecrementBatches(tenantId: Uuid, branchId: Uuid, from: Instant, to: Instant): Int =
        error("not used by the worker")

    override suspend fun listMovements(
        tenantId: Uuid, branchId: Uuid?, ingredientId: Uuid?, from: Instant?, to: Instant?, limit: Int, offset: Int
    ): List<StockMovement> = movements.values.filter { it.tenantId == tenantId }

    override suspend fun listSince(tenantId: Uuid, since: Long, limit: Int): List<StockMovement> =
        movements.values.filter { it.tenantId == tenantId && it.serverSeq > since }.sortedBy { it.serverSeq }.take(limit)
}

class FakeTenantRepository : TenantRepository {
    val tenants = mutableMapOf<Uuid, Tenant>()

    fun seed(tenant: Tenant) {
        tenants[tenant.id] = tenant
    }

    override suspend fun create(id: Uuid, slug: String, name: String, currencyCode: String, enabledModules: Set<Module>): Tenant {
        val tenant = Tenant(id, slug, name, currencyCode, enabledModules, Clock.System.now())
        tenants[id] = tenant
        return tenant
    }

    override suspend fun findById(id: Uuid): Tenant? = tenants[id]
    override suspend fun findBySlug(slug: String): Tenant? = tenants.values.find { it.slug == slug }
    override suspend fun listAll(): List<Tenant> = tenants.values.toList()
    override suspend fun updateEnabledModules(id: Uuid, enabledModules: Set<Module>): Tenant? =
        tenants[id]?.copy(enabledModules = enabledModules)?.also { tenants[id] = it }

    override suspend fun enabledModules(id: Uuid): Set<Module> = tenants[id]?.enabledModules ?: emptySet()
}

class FakeCatalogRepository : CatalogRepository {
    val products = mutableMapOf<Uuid, Product>()
    val formulas = mutableMapOf<Uuid, Formula>()
    private val seq = AtomicLong(0)

    fun seedProduct(id: Uuid, tenantId: Uuid, productType: String = "BREAD") {
        products[id] = Product(id, tenantId, "Test product", productType, SoldBy.WEIGHT, 1000, null, null, null, true, seq.incrementAndGet(), Clock.System.now(), Clock.System.now())
    }

    fun seedFormula(productId: Uuid, tenantId: Uuid, lines: List<FormulaLine>, active: Boolean = true) {
        val formula = Formula(Uuid.random(), tenantId, productId, active, seq.incrementAndGet(), Clock.System.now(), lines)
        formulas[productId] = formula
    }

    override suspend fun createProduct(
        id: Uuid, tenantId: Uuid, name: String, productType: String, soldBy: SoldBy,
        pricePerKgMinor: Long?, priceMinor: Long?, barcode: String?, imageUrl: String?, origin: ProductOrigin, opening: OpeningCount?
    ): Product {
        val product = Product(id, tenantId, name, productType, soldBy, pricePerKgMinor, priceMinor, barcode, imageUrl, true, seq.incrementAndGet(), Clock.System.now(), Clock.System.now())
        products[id] = product
        return product
    }

    override suspend fun findProductById(id: Uuid): Product? = products[id]
    override suspend fun listProducts(tenantId: Uuid, includeInactive: Boolean): List<Product> =
        products.values.filter { it.tenantId == tenantId }

    override suspend fun updateProduct(
        id: Uuid, name: String?, productType: String?, soldBy: SoldBy?, pricePerKgMinor: Long?,
        priceMinor: Long?, barcode: String?, imageUrl: String?, active: Boolean?, origin: ProductOrigin?
    ): Product? = products[id]

    override suspend fun setProductImageUrl(id: Uuid, imageUrl: String?): Product? = products[id]

    override suspend fun setProductLowStockThreshold(id: Uuid, thresholdUnits: Long?): Product? = products[id]

    override suspend fun findFormulaByProduct(tenantId: Uuid, productId: Uuid): Formula? =
        formulas[productId]?.takeIf { it.tenantId == tenantId }

    override suspend fun listFormulas(tenantId: Uuid, includeInactive: Boolean): List<Formula> =
        formulas.values.filter { it.tenantId == tenantId }

    override suspend fun putFormula(id: Uuid, tenantId: Uuid, productId: Uuid, lines: List<FormulaLine>): Formula {
        val formula = Formula(id, tenantId, productId, true, seq.incrementAndGet(), Clock.System.now(), lines)
        formulas[productId] = formula
        return formula
    }
}

class FakeAnalyticsRepository : AnalyticsRepository {
    val facts = mutableListOf<SaleLineFact>()
    var lastQuery: Triple<Uuid, Uuid?, Pair<Instant, Instant>>? = null

    override suspend fun saleLineFacts(tenantId: Uuid, branchId: Uuid?, from: Instant, to: Instant): List<SaleLineFact> {
        lastQuery = Triple(tenantId, branchId, from to to)
        return facts.filter { it.soldAt >= from && it.soldAt <= to && (branchId == null || it.branchId == branchId) }
    }

    override suspend fun productionFacts(tenantId: Uuid, branchId: Uuid?, from: Instant, to: Instant): List<ProductionFact> =
        emptyList()
}

class FakeForecastRepository : ForecastRepository {
    val upserts = mutableListOf<Forecast>()
    var transactions = 0
        private set

    /** Products "deleted while the ML call ran". */
    val unknownProducts = mutableSetOf<Uuid>()

    /** Makes the next [upsertAll] throw, once. */
    var failNext: Exception? = null

    override suspend fun upsertAll(
        tenantId: Uuid, branchId: Uuid, rows: List<ForecastUpsert>, notBefore: LocalDate
    ): ForecastUpsertResult {
        failNext?.let { failNext = null; throw it }
        transactions++
        var past = 0
        var unknown = 0
        for (row in rows) {
            when {
                row.forecastDate < notBefore -> past++
                row.productId in unknownProducts -> unknown++
                else -> upserts += Forecast(
                    Uuid.random(), tenantId, branchId, row.productId, "P-${row.productId}", "Pan", row.forecastDate,
                    row.predictedGrams, row.predictedUnits, row.modelVersion, Clock.System.now()
                )
            }
        }
        return ForecastUpsertResult(rows.size - past - unknown, past, unknown)
    }

    override suspend fun listByBranch(
        tenantId: Uuid, branchId: Uuid, from: LocalDate, to: LocalDate, productType: String?, productId: Uuid?
    ): List<Forecast> = upserts.filter { it.tenantId == tenantId && it.branchId == branchId }
}

/** Echoes each requested series back with [predictionsPerSeries] points from [startDay]. */
class FakeMlForecastClient(
    var modelVersion: String = "lgbm-test",
    var predictedValue: Double = 10.0,
) : MlForecastClient {
    var lastRequest: MlForecastRequest? = null
    val requests = mutableListOf<MlForecastRequest>()

    /** Makes the next [forecast] call throw, once. */
    var failNext: Exception? = null

    override suspend fun forecast(request: MlForecastRequest): MlForecastResponse {
        failNext?.let { failNext = null; throw it }
        lastRequest = request
        requests += request
        val start = LocalDate.parse(request.startDay)
        return MlForecastResponse(
            forecasts = request.series.map { series ->
                MlSeriesForecast(
                    branchId = series.branchId,
                    productType = series.productType,
                    modelVersion = modelVersion,
                    observedDays = series.history.size,
                    predictions = (0 until request.horizonDays).map { offset ->
                        MlForecastPoint(day = start.plus(offset, DateTimeUnit.DAY).toString(), predicted = predictedValue)
                    },
                )
            },
            generatedAt = "2026-07-29T00:00:00Z",
            serviceModelVersion = modelVersion,
        )
    }
}

class FakeBatchEventBroker : BatchEventBroker {
    val published = mutableListOf<BatchProcessed>()

    override suspend fun publishProcessed(event: BatchProcessed) {
        published += event
    }

    override suspend fun consumeProcessed(block: suspend (BatchProcessed) -> Unit) {
        // Unused by processor tests.
    }
}
