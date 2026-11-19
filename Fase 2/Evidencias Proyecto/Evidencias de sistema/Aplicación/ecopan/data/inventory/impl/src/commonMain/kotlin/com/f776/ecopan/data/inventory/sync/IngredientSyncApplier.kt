package com.f776.ecopan.data.inventory.sync

import com.f776.ecopan.domain.inventory.IngredientRepository
import com.f776.ecopan.domain.inventory.IngredientUpsert
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
 * Server-authoritative config applier for `INGREDIENT` (03 §4.3 family 1 — overwrite by id).
 *
 * Without it, ingredients were pulled and dropped: Stock Glance had nothing to name, and
 * `FormulaPreview` refuses a formula whose lines reference an ingredient missing from the local
 * cache (06 §12), so batch logging silently produced no decrement at all.
 *
 * `tenantId` comes from [TenantRepository], not the wire — same rationale as
 * [StockMovementSyncApplier].
 */
internal class IngredientSyncApplier(
    private val ingredientRepository: IngredientRepository,
    private val tenantRepository: TenantRepository,
) : SyncApplier {

    override val entityType: SyncEntityType = SyncEntityType.INGREDIENT

    override suspend fun apply(payload: JsonElement, opType: SyncOpType, serverSeq: Long) {
        val tenantId = tenantRepository.observeTenant().first()?.id ?: return
        val wire = json.decodeFromJsonElement<IngredientPullPayload>(payload)

        ingredientRepository.upsertIngredients(
            listOf(
                IngredientUpsert(
                    id = Uuid.parse(wire.id),
                    tenantId = tenantId,
                    name = wire.name,
                    lowStockThresholdGrams = wire.lowStockThresholdGrams,
                    active = wire.active,
                    serverSeq = serverSeq,
                ),
            ),
        )
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

/** Mirrors the backend's `IngredientPullPayload`. */
@Serializable
private data class IngredientPullPayload(
    val id: String,
    val name: String,
    val lowStockThresholdGrams: Long? = null,
    val active: Boolean = true,
)
