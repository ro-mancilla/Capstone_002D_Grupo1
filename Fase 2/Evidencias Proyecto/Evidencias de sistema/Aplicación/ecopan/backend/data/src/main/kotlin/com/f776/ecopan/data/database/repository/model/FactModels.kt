package com.f776.ecopan.data.database.repository.model

import com.f776.ecopan.data.database.entity.DecrementStatus
import com.f776.ecopan.data.database.entity.PaymentMethod
import com.f776.ecopan.data.database.entity.SoldBy
import com.f776.ecopan.data.database.entity.ProductStockMovementKind
import com.f776.ecopan.data.database.entity.StockMovementKind
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Append-only fact (01 §6.1). `id` is client-minted. */
data class Sale(
    val id: Uuid,
    val tenantId: Uuid,
    val branchId: Uuid,
    val cashierId: Uuid,
    val soldAt: Instant,
    val paymentMethod: PaymentMethod,
    val totalMinor: Long,
    val deviceId: String,
    val serverSeq: Long,
    val receivedAt: Instant,
    val lines: List<SaleLine>
)

data class SaleLine(
    val id: Uuid,
    val saleId: Uuid,
    val productId: Uuid,
    val soldBy: SoldBy,
    val weightGrams: Long?,
    val quantity: Int?,
    val unitPriceMinor: Long,
    val lineTotalMinor: Long
)

/** Input shape for a line being ingested with its parent [Sale] (02 §4.1). */
/**
 * One whole sale plus its lines, for [com.f776.ecopan.data.database.repository.SaleRepository.createAllWithLines].
 * Only used by bulk paths -- the single-sale ingest path takes these as separate parameters.
 */
data class SaleInput(
    val id: Uuid,
    val tenantId: Uuid,
    val branchId: Uuid,
    val cashierId: Uuid,
    val soldAt: Instant,
    val paymentMethod: PaymentMethod,
    val totalMinor: Long,
    val deviceId: String,
    val lines: List<SaleLineInput>
)

data class SaleLineInput(
    val id: Uuid,
    val productId: Uuid,
    val soldBy: SoldBy,
    val weightGrams: Long?,
    val quantity: Int?,
    val unitPriceMinor: Long,
    val lineTotalMinor: Long
)

/** Append-only fact; `decrementStatus` is written by the worker (02 §6, out of this item's scope). */
data class ProductionBatch(
    val id: Uuid,
    val tenantId: Uuid,
    val branchId: Uuid,
    val bakerId: Uuid,
    val productId: Uuid,
    val mixWeightGrams: Long,
    val batchAt: Instant,
    val movementsMinted: Boolean,
    val decrementStatus: DecrementStatus,
    val deviceId: String,
    val serverSeq: Long,
    val receivedAt: Instant
)

/** Append-only delta ledger row (01 §6.1) -- current stock = Σ deltas for an ingredient. */
data class StockMovement(
    val id: Uuid,
    val tenantId: Uuid,
    val branchId: Uuid,
    val ingredientId: Uuid,
    val deltaGrams: Long,
    val kind: StockMovementKind,
    val sourceBatchId: Uuid?,
    val note: String?,
    val createdBy: Uuid?,
    val occurredAt: Instant,
    val serverSeq: Long,
    val receivedAt: Instant
)

/** Derived current stock (Σ movements) for one ingredient in one branch (02 §3 I3). */
data class IngredientStock(val ingredientId: Uuid, val currentGrams: Long)

/**
 * One ingredient's ledger over a closed window `[from, to)` in one branch. Every figure is a signed
 * Σ of `delta_grams`: [decrementedGrams] is therefore <= 0 and [adjustedGrams] carries its own sign,
 * which is what makes `opening + purchased + decremented + adjusted == closing` hold by construction.
 * [batchCount] is the number of distinct production batches behind [decrementedGrams].
 */
data class IngredientReconciliation(
    val ingredientId: Uuid,
    val openingGrams: Long,
    val purchasedGrams: Long,
    val decrementedGrams: Long,
    val adjustedGrams: Long,
    val batchCount: Int
) {
    val closingGrams: Long get() = openingGrams + purchasedGrams + decrementedGrams + adjustedGrams
}

/**
 * Idempotent-ingest result (01 §6.3, 02 §1): [Applied] for a brand-new row, [Duplicate] when the
 * `INSERT ... ON CONFLICT (id) DO NOTHING` hit an existing row -- [value] is always the persisted
 * row either way (existing or freshly inserted), so callers always have a `serverSeq` to ack with.
 */
sealed interface IngestResult<out T> {
    val value: T

    data class Applied<T>(override val value: T) : IngestResult<T>
    data class Duplicate<T>(override val value: T) : IngestResult<T>
}

/** One row of the resold-product unit ledger ([ProductStockMovementTable]). */
data class ProductStockMovement(
    val id: Uuid,
    val tenantId: Uuid,
    val branchId: Uuid,
    val productId: Uuid,
    val deltaUnits: Long,
    val kind: ProductStockMovementKind,
    val note: String?,
    val createdBy: Uuid?,
    val occurredAt: Instant,
    val receivedAt: Instant
)

/**
 * Σ movements (opening count included) for one product in one branch, plus [openingReceivedAt]: the
 * *server receipt time* (`received_at`, not the client-supplied `occurred_at`) of the branch's
 * earliest `OPENING_COUNT`. Sales start counting against the stock from this instant, and sales
 * carry server receipt time too, so both sides of the comparison are on the server clock. Null when
 * the branch has no opening count; the caller then counts from the product's creation.
 */
data class ProductStockSum(val productId: Uuid, val receivedUnits: Long, val openingReceivedAt: Instant?)

/** One ingredient line of a formula decrement: [deltaGrams] is negative (stock consumed). */
data class DecrementMovement(val ingredientId: Uuid, val deltaGrams: Long)
