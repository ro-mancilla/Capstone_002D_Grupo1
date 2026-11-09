package com.f776.ecopan.domain.sync

import kotlinx.serialization.json.JsonElement

/**
 * How a pulled row (03 §4.2/§4.3) gets written to this client's local cache. Each owning
 * `:data:*:impl` module (e.g. `:data:inventory:impl` for `STOCK_MOVEMENT`, `:data:tenant:impl` for
 * `BRANCH`) implements one of these per [entityType] it understands and binds it into its own
 * Koin module (`single { ... }.bind<SyncApplier>()`); `:data:sync:impl`'s `PullWorker` collects
 * every bound instance via `getKoin().getAll<SyncApplier>()` and dispatches by [entityType] — it
 * never depends on the owning modules directly (dependency direction stays `:data:*:impl` →
 * `:data:sync:api`, never the reverse, 03 §1.1).
 *
 * This deliberately lives in `:data:sync:api` (public), not as an `internal` type in
 * `:data:sync:impl` as 03 §1.1's illustrative sketch suggests: Kotlin's `internal` visibility is
 * scoped to a single Gradle module, so an `internal interface` in `:data:sync:impl` could not be
 * implemented by classes compiled in a different Gradle module (`:data:inventory:impl`, etc.) at
 * all — that's precisely 03's Open Question 5 ("Koin multibinding is not first-class"). Moving the
 * *interface* (not the collection mechanism) into `:data:sync:api` resolves it: every applier
 * owner already depends on `:data:sync:api` for [SyncEntityType]/[SyncOpType], so implementing
 * this contract there costs nothing extra, and `getAll<SyncApplier>()` still works exactly as
 * planned because it operates on the Koin container at runtime, not on module compile boundaries.
 *
 * Two applier families exist by convention (03 §4.3), chosen per-[entityType] by what the
 * implementation's [apply] body does — not by any branching here or in `PullWorker`:
 * 1. **Config (server-authoritative, LWW):** overwrite the local cache row by id (`@Upsert`).
 * 2. **Append-only facts:** insert-if-unseen (`INSERT OR IGNORE`) by the row's client-minted UUID
 *    primary key; never update, never delete.
 *
 * [apply] must, like inserting an [outboxRow], perform only plain DAO work with no transaction or
 * dispatcher hop of its own — `PullWorker` calls every applier for one pulled page inside a single
 * ambient Room transaction (03 §4.1) so a crash mid-page can't apply half a page.
 */
interface SyncApplier {
    val entityType: SyncEntityType

    /** [serverSeq] is the pulled row's own `ChangeDto.serverSeq` (02 §4.2) -- config appliers
     *  typically persist it (e.g. `BranchUpsert.serverSeq`) for the sync layer's own bookkeeping;
     *  fact appliers usually ignore it, since fact ordering is per-client-FIFO, not server-order. */
    suspend fun apply(payload: JsonElement, opType: SyncOpType, serverSeq: Long)
}
