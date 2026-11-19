package com.f776.ecopan.api.feature.sync.domain

import com.f776.ecopan.api.feature.sync.controller.dto.MutationDto
import com.f776.ecopan.api.feature.sync.controller.dto.MutationResultDto
import com.f776.ecopan.api.feature.sync.controller.dto.ProductionBatchPayload
import com.f776.ecopan.api.feature.sync.controller.dto.PushResponse
import com.f776.ecopan.api.feature.sync.controller.dto.SalePayload
import com.f776.ecopan.api.feature.sync.controller.dto.StockMovementPayload
import com.f776.ecopan.api.feature.sync.controller.dto.SyncErrorDto
import com.f776.ecopan.data.database.entity.PaymentMethod
import com.f776.ecopan.data.database.entity.SoldBy
import com.f776.ecopan.data.database.entity.StockMovementKind
import com.f776.ecopan.data.database.entity.ProductOrigin
import com.f776.ecopan.data.database.repository.CatalogRepository
import com.f776.ecopan.data.database.repository.ProductionBatchRepository
import com.f776.ecopan.data.database.repository.SaleRepository
import com.f776.ecopan.data.database.repository.StockMovementRepository
import com.f776.ecopan.data.database.repository.model.IngestResult
import com.f776.ecopan.data.database.repository.model.SaleLineInput
import com.f776.ecopan.data.domain.SyncEntityType
import kotlinx.serialization.json.Json
import java.sql.SQLException
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Rejection thrown by payload validation inside [processOne] -- caught once at the call site and
 * turned into a `REJECTED` result so one bad mutation never aborts the rest of the batch. `REJECTED`
 * is for the item's own fault (validation, a domain rule): the cashier has to act on it. An
 * infrastructure failure is not caught here and fails the whole request instead (#135) (02 §4.1:
 * "Whole-request failures ... leave the outbox untouched" is about the *whole request*, not one
 * mutation; each mutation is handled independently here).
 */
private class MutationRejected(val code: String, message: String) : Exception(message)

internal class SyncIngestServiceImpl(
    private val saleRepository: SaleRepository,
    private val productionBatchRepository: ProductionBatchRepository,
    private val catalogRepository: CatalogRepository,
    private val stockMovementRepository: StockMovementRepository,
) : SyncIngestService {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun push(
        tenantId: Uuid,
        principalUserId: Uuid,
        principalBranchId: Uuid?,
        deviceId: String,
        mutations: List<MutationDto>
    ): PushResponse {
        val results = mutations.map { mutation -> processOne(tenantId, principalUserId, principalBranchId, deviceId, mutation) }
        return PushResponse(serverTime = Clock.System.now().toEpochMilliseconds(), results = results)
    }

    private suspend fun processOne(
        tenantId: Uuid,
        principalUserId: Uuid,
        principalBranchId: Uuid?,
        deviceId: String,
        mutation: MutationDto
    ): MutationResultDto = try {
        when (mutation.entityType) {
            SyncEntityType.SALE.name -> ingestSale(tenantId, principalBranchId, deviceId, mutation)
            SyncEntityType.PRODUCTION_BATCH.name -> ingestProductionBatch(tenantId, principalBranchId, deviceId, mutation)
            SyncEntityType.STOCK_MOVEMENT.name -> ingestStockMovement(tenantId, principalUserId, principalBranchId, mutation)
            else -> throw MutationRejected("UNKNOWN_ENTITY_TYPE", "unsupported push entityType: ${mutation.entityType}")
        }
    } catch (rejected: MutationRejected) {
        MutationResultDto(mutation.mutationId, "REJECTED", error = SyncErrorDto(rejected.code, rejected.message))
    } catch (e: Exception) {
        // Only an infrastructure failure -- the database or network, not the item -- fails the whole
        // push (5xx, the till retries; #135). Anything else is the item's fault and is REJECTED, as
        // before: rethrowing it would make the till retry a bad item forever and block its queue.
        if (e.isInfrastructureFailure()) throw e
        MutationResultDto(mutation.mutationId, "REJECTED", error = SyncErrorDto("VALIDATION_ERROR", e.message))
    }

    private fun requireBranchAllowed(branchId: Uuid, principalBranchId: Uuid?) {
        if (principalBranchId != null && principalBranchId != branchId) {
            throw MutationRejected("BRANCH_FORBIDDEN", "principal is scoped to a different branch")
        }
    }

    private fun parseUuid(value: String, field: String): Uuid =
        runCatching { Uuid.parse(value) }.getOrNull() ?: throw MutationRejected("VALIDATION_ERROR", "$field is not a valid uuid")

    private inline fun <reified E : Enum<E>> parseEnum(value: String, field: String): E =
        enumValues<E>().firstOrNull { it.name == value } ?: throw MutationRejected(
            "VALIDATION_ERROR",
            "$field must be one of ${enumValues<E>().joinToString { it.name }}, got '$value'"
        )

    private fun <T> IngestResult<T>.toStatus(): String = when (this) {
        is IngestResult.Applied -> "APPLIED"
        is IngestResult.Duplicate -> "DUPLICATE"
    }

    private suspend fun ingestSale(tenantId: Uuid, principalBranchId: Uuid?, deviceId: String, mutation: MutationDto): MutationResultDto {
        val payload = json.decodeFromJsonElement<SalePayload>(mutation.payload)
        val branchId = parseUuid(payload.branchId, "branchId")
        requireBranchAllowed(branchId, principalBranchId)

        val lines = payload.lines.map { line ->
            SaleLineInput(
                id = parseUuid(line.id, "lines[].id"),
                productId = parseUuid(line.productId, "lines[].productId"),
                soldBy = parseEnum<SoldBy>(line.soldBy, "lines[].soldBy"),
                weightGrams = line.weightGrams,
                quantity = line.quantity,
                unitPriceMinor = line.unitPriceMinor,
                lineTotalMinor = line.lineTotalMinor
            )
        }

        val result = saleRepository.createWithLines(
            id = parseUuid(payload.id, "id"),
            tenantId = tenantId,
            branchId = branchId,
            cashierId = parseUuid(payload.cashierId, "cashierId"),
            soldAt = Instant.fromEpochMilliseconds(payload.soldAt),
            paymentMethod = parseEnum<PaymentMethod>(payload.paymentMethod, "paymentMethod"),
            totalMinor = payload.totalMinor,
            deviceId = deviceId,
            lines = lines
        )
        return MutationResultDto(mutation.mutationId, result.toStatus(), serverSeq = result.value.serverSeq)
    }

    private suspend fun ingestProductionBatch(
        tenantId: Uuid,
        principalBranchId: Uuid?,
        deviceId: String,
        mutation: MutationDto
    ): MutationResultDto {
        val payload = json.decodeFromJsonElement<ProductionBatchPayload>(mutation.payload)
        val branchId = parseUuid(payload.branchId, "branchId")
        requireBranchAllowed(branchId, principalBranchId)

        val productId = parseUuid(payload.productId, "productId")
        // Production is Panadería-only (spec #22): an ALMACEN product is bought finished, never baked.
        val product = catalogRepository.findProductById(productId)
        if (product != null && product.tenantId == tenantId && product.origin == ProductOrigin.ALMACEN) {
            throw MutationRejected("PRODUCTION_REQUIRES_PANADERIA", "an ALMACEN product cannot be produced")
        }

        val result = productionBatchRepository.create(
            id = parseUuid(payload.id, "id"),
            tenantId = tenantId,
            branchId = branchId,
            bakerId = parseUuid(payload.bakerId, "bakerId"),
            productId = productId,
            mixWeightGrams = payload.mixWeightGrams,
            batchAt = Instant.fromEpochMilliseconds(payload.batchAt),
            movementsMinted = payload.movementsMinted,
            deviceId = deviceId
        )
        // 02 §6: a fresh `Applied` insert has already enqueued its `ProductionBatchMessage` on the
        // outbox, in the same transaction (#142); the app's outbox relay publishes it. A broker
        // outage therefore never reaches this response, and a `Duplicate` never announces twice.
        return MutationResultDto(mutation.mutationId, result.toStatus(), serverSeq = result.value.serverSeq)
    }

    private suspend fun ingestStockMovement(
        tenantId: Uuid,
        principalUserId: Uuid,
        principalBranchId: Uuid?,
        mutation: MutationDto
    ): MutationResultDto {
        val payload = json.decodeFromJsonElement<StockMovementPayload>(mutation.payload)
        val branchId = parseUuid(payload.branchId, "branchId")
        requireBranchAllowed(branchId, principalBranchId)

        val kind = runCatching { StockMovementKind.valueOf(payload.kind) }.getOrNull()
            ?: throw MutationRejected("VALIDATION_ERROR", "unknown stock movement kind: ${payload.kind}")

        val sourceBatchId = payload.sourceBatchId?.let { parseUuid(it, "sourceBatchId") }
        if (kind == StockMovementKind.FORMULA_DECREMENT) {
            if (sourceBatchId == null) {
                throw MutationRejected("INVALID_SOURCE_BATCH", "FORMULA_DECREMENT requires sourceBatchId")
            }
            val batch = productionBatchRepository.findById(sourceBatchId)
            if (batch == null || batch.tenantId != tenantId || batch.branchId != branchId) {
                throw MutationRejected("INVALID_SOURCE_BATCH", "sourceBatchId does not reference a batch of this tenant/branch")
            }
        }

        val result = stockMovementRepository.append(
            id = parseUuid(payload.id, "id"),
            tenantId = tenantId,
            branchId = branchId,
            ingredientId = parseUuid(payload.ingredientId, "ingredientId"),
            deltaGrams = payload.deltaGrams,
            kind = kind,
            sourceBatchId = sourceBatchId,
            note = payload.note,
            createdBy = principalUserId,
            occurredAt = Instant.fromEpochMilliseconds(payload.occurredAt)
        )
        return MutationResultDto(mutation.mutationId, result.toStatus(), serverSeq = result.value.serverSeq)
    }
}

/**
 * A dropped connection, a timeout, a serialization failure: anything retrying could fix. An SQL
 * integrity violation (SQLSTATE class 23 -- a dangling reference, a broken check) is the item's
 * fault and is not one of these.
 */
private fun Throwable.isInfrastructureFailure(): Boolean = generateSequence(this) { it.cause }.any { cause ->
    when (cause) {
        is java.io.IOException -> true
        is SQLException -> cause.sqlState?.startsWith("23") != true
        else -> false
    }
}
