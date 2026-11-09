package com.f776.ecopan.data.inventory.sync

import com.f776.ecopan.domain.inventory.MovementKind
import com.f776.ecopan.domain.sync.SyncApplier
import com.f776.ecopan.domain.sync.SyncEntityType
import com.f776.ecopan.domain.sync.SyncOpType
import com.f776.ecopan.domain.tenant.TenantRepository
import com.f776.unigrid.core.database.dao.StockMovementDao
import com.f776.unigrid.core.database.entity.StockMovementEntity
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Pull-side applier for `STOCK_MOVEMENT` -- an append-only fact (03 §4.3 family 2): insert if
 * unseen by this row's client-minted UUID id, never update, never delete. This also covers the
 * client's own pushed rows echoing back on the next pull (03 §4.2/§4.3: "no echo-suppression
 * logic needed") -- [StockMovementDao.insertIfUnseen] silently no-ops for an id already present.
 *
 * Payload shape mirrors the backend's `StockMovementPayload` (02 §4.1/§4.2 -- "STOCK_MOVEMENT
 * reuses StockMovementPayload from the push side"): `occurredAt` is epoch millis on the wire, and
 * there is no `createdBy` field server-side, so [StockMovementEntity.recordedBy] is always `null`
 * for a pulled row. `tenantId` also isn't on the wire (it's the JWT principal on push, 02 §4.1) --
 * this applier resolves it from [TenantRepository] instead, relying on 01 §4's invariant that a
 * device caches exactly one tenant at a time (the same assumption `CursorStore`/`TenantRepositoryImpl`
 * already make); a pull with no cached tenant yet (pre-bootstrap) is a no-op, since bootstrap (03
 * §8) always pulls the tenant/branch config pages before any fact page in the real ordering.
 *
 * Public (not `internal`): `:data:sync:impl`'s `EndToEndBatchSyncTest` (plan 06 item 10's
 * cross-device acceptance test) constructs this directly to exercise the real applier rather than
 * a generic stand-in, since production wiring only reaches it via Koin's `getAll<SyncApplier>()`
 * (see [com.f776.ecopan.domain.sync.SyncApplier]'s KDoc) -- that dispatch mechanism, not this
 * class's visibility, is what keeps `:data:sync` decoupled from `:data:inventory:impl`.
 */
class StockMovementSyncApplier(
    private val stockMovementDao: StockMovementDao,
    private val tenantRepository: TenantRepository,
) : SyncApplier {

    override val entityType: SyncEntityType = SyncEntityType.STOCK_MOVEMENT

    override suspend fun apply(payload: JsonElement, opType: SyncOpType, serverSeq: Long) {
        val tenantId = tenantRepository.observeTenant().first()?.id ?: return
        val wire = json.decodeFromJsonElement<StockMovementPullPayload>(payload)
        val kind = runCatching { MovementKind.valueOf(wire.kind) }.getOrNull() ?: return

        stockMovementDao.insertIfUnseen(
            listOf(
                StockMovementEntity(
                    id = Uuid.parse(wire.id),
                    tenantId = tenantId,
                    branchId = Uuid.parse(wire.branchId),
                    ingredientId = Uuid.parse(wire.ingredientId),
                    deltaGrams = wire.deltaGrams,
                    kind = kind.name,
                    sourceBatchId = wire.sourceBatchId?.let { Uuid.parse(it) },
                    note = wire.note,
                    recordedAt = Instant.fromEpochMilliseconds(wire.occurredAt),
                    recordedBy = null,
                ),
            ),
        )
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
private data class StockMovementPullPayload(
    val id: String,
    val branchId: String,
    val ingredientId: String,
    val deltaGrams: Long,
    val kind: String,
    val note: String? = null,
    val occurredAt: Long,
    val sourceBatchId: String? = null,
)
