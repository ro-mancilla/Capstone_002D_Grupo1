package com.f776.ecopan.data.tenant.sync

import com.f776.ecopan.domain.sync.SyncApplier
import com.f776.ecopan.domain.sync.SyncEntityType
import com.f776.ecopan.domain.sync.SyncOpType
import com.f776.ecopan.domain.tenant.BranchUpsert
import com.f776.ecopan.domain.tenant.TenantRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.uuid.Uuid

/**
 * Pull-side applier for `BRANCH` -- server-authoritative config (03 §4.3 family 1): overwrite the
 * local cache row by id, regardless of [opType] (there's no "reject the pull" case for a
 * read-only cache, 03 §5 table: "local edits to config from offline clients don't exist by
 * construction"). Reuses [TenantRepository.upsertBranches], the same write path the future
 * bootstrap pull (03 §8) will call for the initial full-catalog fetch, so there is exactly one
 * place `BranchEntity` rows get written from the wire.
 *
 * Public (not `internal`), for the same reason [com.f776.ecopan.data.inventory.sync.StockMovementSyncApplier]
 * is: `:data:sync:impl`'s `ConflictPolicyTest` (03 §5 test 2, `configLastWriteWinsByServerSeq`)
 * constructs this directly so the LWW-by-`serverSeq` proof runs against the real config applier
 * rather than a stand-in. Production wiring still only reaches it via Koin's `getAll<SyncApplier>()`
 * (see [SyncApplier]'s KDoc) -- that dispatch mechanism, not this class's visibility, is what keeps
 * `:data:sync` decoupled from `:data:tenant:impl`.
 */
class BranchSyncApplier(
    private val tenantRepository: TenantRepository,
) : SyncApplier {

    override val entityType: SyncEntityType = SyncEntityType.BRANCH

    override suspend fun apply(payload: JsonElement, opType: SyncOpType, serverSeq: Long) {
        val tenantId = tenantRepository.observeTenant().first()?.id ?: return
        val wire = json.decodeFromJsonElement<BranchPullPayload>(payload)

        tenantRepository.upsertBranches(
            listOf(
                BranchUpsert(
                    id = Uuid.parse(wire.id),
                    tenantId = tenantId,
                    name = wire.name,
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

/** Mirrors the backend's `BranchPullPayload` exactly (`id`, `name`, `active`) -- `serverSeq` comes from the envelope's own `ChangeDto.serverSeq`, passed in separately by [PullWorker]. */
@Serializable
private data class BranchPullPayload(val id: String, val name: String, val active: Boolean)
