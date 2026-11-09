package com.f776.ecopan.worker.domain

import com.f776.ecopan.data.database.entity.DecrementStatus
import com.f776.ecopan.data.database.repository.CatalogRepository
import com.f776.ecopan.data.database.repository.ProductionBatchRepository
import com.f776.ecopan.data.database.repository.TenantRepository
import com.f776.ecopan.data.database.repository.model.DecrementMovement
import com.f776.ecopan.data.database.repository.model.ProductionBatch
import com.f776.ecopan.data.domain.Module
import com.f776.ecopan.data.messagebroker.model.ProductionBatchMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import java.io.IOException
import java.sql.SQLException
import java.sql.SQLRecoverableException
import java.sql.SQLTransientException
import java.util.concurrent.TimeoutException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * Fallback-only formula decrement (01 §8, 02 §6). Formula decrement is computed **client-side** by
 * default (the baker device mints the movements in the batch's local transaction); this processor
 * only does real work when a batch synced with `movementsMinted = false`.
 *
 * [decrementEnabled] is the amputation switch (`DECREMENT_ENABLED=false`, 02 §7): with it off, every
 * batch short-circuits to `SKIPPED` with zero movements written, regardless of formula/module state.
 *
 * All-or-nothing (#143): the outcome -- movements, status and the `BatchProcessed` notice, which the
 * worker's outbox relay publishes -- is written by one [ProductionBatchRepository.completeDecrement]
 * transaction, so the ledger never holds half a batch. A failure is then either:
 *
 * - **transient** (connection, timeout, serialization/deadlock): retried here up to [maxAttempts]
 *   times with backoff, then rethrown so the broker dead-letters it. The batch stays `PENDING`.
 * - **deterministic** (anything else -- retrying cannot change it): the batch is marked `FAILED` and
 *   the message rethrown at once, to be dead-lettered.
 *
 * A batch that is no longer `PENDING` is acknowledged and skipped, and a later failure never
 * overwrites an outcome already written: `completeDecrement` only moves a `PENDING` batch.
 */
class FormulaDecrementProcessorImpl(
    private val productionBatchRepository: ProductionBatchRepository,
    private val catalogRepository: CatalogRepository,
    private val tenantRepository: TenantRepository,
    private val decrementEnabled: Boolean,
    private val maxAttempts: Int = 5,
    private val retryDelay: (attempt: Int) -> Duration = { attempt -> 1.seconds * (1 shl (attempt - 1)) },
) : FormulaDecrementProcessor {
    private val logger = LoggerFactory.getLogger(FormulaDecrementProcessorImpl::class.java)

    override suspend fun processMessage(message: ProductionBatchMessage) {
        val batchId = Uuid.parse(message.batchId)
        var attempt = 1
        while (true) {
            try {
                return processOnce(batchId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!e.isTransient()) {
                    logger.error("Formula decrement failed for batch $batchId and cannot succeed on retry; marking FAILED", e)
                    try {
                        productionBatchRepository.completeDecrement(batchId, DecrementStatus.FAILED, emptyList())
                    } catch (markFailure: Exception) {
                        if (markFailure is CancellationException) throw markFailure
                        logger.error("Could not mark batch $batchId FAILED", markFailure)
                    }
                    throw e
                }
                if (attempt >= maxAttempts) {
                    logger.error("Formula decrement for batch $batchId failed transiently $attempt times; dead-lettering, batch left PENDING", e)
                    throw e
                }
                val wait = retryDelay(attempt)
                logger.warn("Formula decrement for batch $batchId failed transiently (attempt $attempt/$maxAttempts); retrying in $wait", e)
                delay(wait)
                attempt += 1
            }
        }
    }

    private suspend fun processOnce(batchId: Uuid) {
        val batch = productionBatchRepository.findById(batchId)
        if (batch == null) {
            logger.warn("ProductionBatchMessage for unknown batch $batchId; dropping")
            return
        }
        // Idempotent redelivery: a terminal status means this message was already handled.
        if (batch.decrementStatus != DecrementStatus.PENDING) {
            logger.info("Batch $batchId already ${batch.decrementStatus}; skipping redelivery")
            return
        }
        val (status, movements) = outcome(batch)
        if (!productionBatchRepository.completeDecrement(batch.id, status, movements)) {
            logger.info("Batch $batchId was completed by another delivery meanwhile; nothing written")
        }
    }

    private suspend fun outcome(batch: ProductionBatch): Pair<DecrementStatus, List<DecrementMovement>> {
        // The client already minted the movements and pushed its own STOCK_MOVEMENT rows.
        if (batch.movementsMinted) return DecrementStatus.APPLIED to emptyList()

        // Cut-line checks -- switch off, module off or no active formula -> SKIPPED, still success.
        if (!decrementEnabled) return DecrementStatus.SKIPPED to emptyList()
        if (Module.INVENTORY_FORMULAS !in tenantRepository.enabledModules(batch.tenantId)) return DecrementStatus.SKIPPED to emptyList()
        val formula = catalogRepository.findFormulaByProduct(batch.tenantId, batch.productId)
        if (formula == null || !formula.active || formula.lines.isEmpty()) return DecrementStatus.SKIPPED to emptyList()

        // Largest-remainder integer math (01 §5); booked at the batch's own batchAt (#137).
        val derived = FormulaMath.derive(batch.mixWeightGrams, formula.lines.map { FormulaMath.Line(it.ingredientId, it.basisPoints) })
            ?: return DecrementStatus.SKIPPED to emptyList()
        return DecrementStatus.APPLIED to derived.map { DecrementMovement(it.ingredientId, -it.grams) }
    }
}

/**
 * Whether retrying could succeed: the database or network failed, not the batch. Walks the cause
 * chain, because Exposed wraps the driver's exception.
 */
internal fun Throwable.isTransient(): Boolean = generateSequence(this) { it.cause }.any { cause ->
    when (cause) {
        is SQLTransientException, is SQLRecoverableException, is IOException, is TimeoutException -> true
        is SQLException -> cause.sqlState?.let { state ->
            // 08: connection; 40001/40P01: serialization failure/deadlock; 57P0x: server shutting down; 53300: too many connections.
            state.startsWith("08") || state in setOf("40001", "40P01", "57P01", "57P02", "57P03", "53300")
        } == true
        else -> false
    }
}
