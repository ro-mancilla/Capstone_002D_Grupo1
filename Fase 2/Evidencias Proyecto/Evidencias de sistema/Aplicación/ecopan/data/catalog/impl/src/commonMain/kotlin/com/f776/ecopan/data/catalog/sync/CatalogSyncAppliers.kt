package com.f776.ecopan.data.catalog.sync

import com.f776.ecopan.core.domain.catalog.ProductOrigin
import com.f776.ecopan.core.domain.catalog.SoldBy
import com.f776.ecopan.domain.catalog.FormulaLine
import com.f776.ecopan.domain.catalog.FormulaRepository
import com.f776.ecopan.domain.catalog.FormulaUpsert
import com.f776.ecopan.domain.catalog.ProductRepository
import com.f776.ecopan.domain.catalog.ProductUpsert
import com.f776.ecopan.domain.sync.SyncApplier
import com.f776.ecopan.domain.sync.SyncEntityType
import com.f776.ecopan.domain.sync.SyncOpType
import com.f776.ecopan.domain.tenant.TenantRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.uuid.Uuid

/**
 * Server-authoritative config appliers (03 §4.3 family 1: overwrite the local cache by id).
 *
 * These did not exist. `PullWorker` skips any pulled row whose `entityType` has no bound applier,
 * so `PRODUCT`, `INGREDIENT`, `FORMULA` and `USER` were fetched from the backend
 * on every sync and silently dropped — the catalog never reached the device, and both the POS
 * product grid and the baker's product list came up empty against a fully-seeded backend.
 *
 * `tenantId` is not on the wire (it is the JWT principal, 02 §4.1) and is resolved from
 * [TenantRepository], exactly as `BranchSyncApplier` and `StockMovementSyncApplier` do, relying on
 * 01 §4's one-tenant-per-device invariant. A pull before any tenant is cached is a no-op; bootstrap
 * (03 §8) caches the tenant from the login response before the first pull, so this cannot lose data.
 */
internal class ProductSyncApplier(
    private val productRepository: ProductRepository,
    private val tenantRepository: TenantRepository,
) : SyncApplier {

    override val entityType: SyncEntityType = SyncEntityType.PRODUCT

    override suspend fun apply(payload: JsonElement, opType: SyncOpType, serverSeq: Long) {
        val tenantId = tenantRepository.observeTenant().first()?.id ?: return
        val wire = catalogJson.decodeFromJsonElement<ProductPullPayload>(payload)
        val soldBy = runCatching { SoldBy.valueOf(wire.soldBy) }.getOrNull() ?: return
        // A payload from a server predating origin omits it; every such product was baked.
        val origin = wire.origin?.let { runCatching { ProductOrigin.valueOf(it) }.getOrNull() } ?: ProductOrigin.PANADERIA

        productRepository.upsertProducts(
            listOf(
                ProductUpsert(
                    id = Uuid.parse(wire.id),
                    tenantId = tenantId,
                    name = wire.name,
                    productType = wire.productType,
                    soldBy = soldBy,
                    pricePerKgMinor = wire.pricePerKgMinor,
                    priceMinor = wire.priceMinor,
                    barcode = wire.barcode,
                    imageUrl = wire.imageUrl,
                    active = wire.active,
                    serverSeq = serverSeq,
                    // Not carried by the backend's ProductPullPayload. Batch entry treats a null
                    // ratio as "no unit estimate" and falls back to default mix bounds, so these
                    // stay null until the server-side payload grows them.
                    mixToLoafRatioMilli = null,
                    minMixGrams = null,
                    maxMixGrams = null,
                    origin = origin,
                    lowStockThresholdUnits = wire.lowStockThresholdUnits,
                    stockUnits = wire.stockUnits,
                ),
            ),
        )
    }
}

internal class FormulaSyncApplier(
    private val formulaRepository: FormulaRepository,
    private val tenantRepository: TenantRepository,
) : SyncApplier {

    override val entityType: SyncEntityType = SyncEntityType.FORMULA

    override suspend fun apply(payload: JsonElement, opType: SyncOpType, serverSeq: Long) {
        val tenantId = tenantRepository.observeTenant().first()?.id ?: return
        val wire = catalogJson.decodeFromJsonElement<FormulaPullPayload>(payload)

        formulaRepository.upsertFormula(
            FormulaUpsert(
                id = Uuid.parse(wire.id),
                tenantId = tenantId,
                productId = Uuid.parse(wire.productId),
                active = wire.active,
                serverSeq = serverSeq,
                // Lines replace the formula's whole line set (02 §1 "replaced wholesale on PUT"),
                // which is also what makes a re-pulled page idempotent here.
                lines = wire.lines.map { FormulaLine(ingredientId = Uuid.parse(it.ingredientId), basisPoints = it.basisPoints) },
            ),
        )
    }
}

private val catalogJson = Json { ignoreUnknownKeys = true }

/** Mirrors the backend's `ProductPullPayload` (`SyncPullServiceImpl.toPayload`). */
@Serializable
private data class ProductPullPayload(
    val id: String,
    val name: String,
    val productType: String,
    val soldBy: String,
    val pricePerKgMinor: Long? = null,
    val priceMinor: Long? = null,
    val barcode: String? = null,
    val imageUrl: String? = null,
    val active: Boolean = true,
    val origin: String? = null,
    // Absent from servers that predate #26 and for Panadería products; both mean "no stock info".
    val lowStockThresholdUnits: Long? = null,
    val stockUnits: Long? = null,
)

/** Mirrors the backend's `FormulaPullPayload`/`FormulaLinePullPayload`. */
@Serializable
private data class FormulaPullPayload(
    val id: String,
    val productId: String,
    val active: Boolean = true,
    val lines: List<FormulaLinePullPayload> = emptyList(),
)

@Serializable
private data class FormulaLinePullPayload(val id: String, val ingredientId: String, val basisPoints: Int)
