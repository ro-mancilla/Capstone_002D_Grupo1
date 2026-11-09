package com.f776.ecopan.data.sync.pull

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.f776.ecopan.data.sync.CursorStore
import com.f776.ecopan.data.sync.transport.NoOpSyncAuthProvider
import com.f776.ecopan.data.sync.transport.SyncAuthException
import com.f776.ecopan.data.sync.transport.SyncRefreshOutcome
import com.f776.ecopan.data.sync.transport.SyncTransientException
import com.f776.ecopan.data.sync.transport.SyncAuthProvider
import com.f776.ecopan.data.sync.transport.SyncTransport
import com.f776.ecopan.domain.sync.SyncApplier
import com.f776.ecopan.domain.sync.SyncEntityType
import com.f776.ecopan.domain.sync.SyncOpType
import com.f776.ecopan.domain.sync.wire.ChangeDto
import com.f776.unigrid.core.database.AppDatabase
import kotlinx.coroutines.flow.first
import kotlin.time.Clock

internal data class PullOutcome(val pagesApplied: Int, val changesApplied: Int)

/**
 * Pages `GET /sync/pull` from [CursorStore]'s cursor (03 §4.1), applying each page's rows and
 * advancing the cursor. Per-page steps run in this order: (1) apply every row in the page inside
 * one ambient Room transaction ([AppDatabase.useWriterConnection]/`immediateTransaction`, same
 * primitive `OutboxAtomicityTest` uses), (2) only once that transaction commits,
 * persist [CursorStore.advanceTo] for the page's `nextCursor`.
 *
 * This is deliberately *not* one atomic unit spanning both stores -- [CursorStore] is
 * DataStore-backed (03 item 3's already-committed design, §4.1), and DataStore can't join a Room
 * transaction. The documented tradeoff (03 §4.1's "crash mid-page re-pulls the page; appliers are
 * idempotent so that's safe" already anticipates exactly this): if the process dies between (1)
 * and (2), the cursor is still at the *previous* value, so the next run re-requests the same page
 * from the server and re-applies it -- safe because every [SyncApplier] is either an idempotent
 * upsert-by-id (config) or an idempotent insert-if-unseen (facts), never a blind append. No row
 * is ever skipped (the cursor only ever moves forward after a successful apply) and no row is
 * ever double-counted in the domain data (that's what applier idempotency buys back).
 */
internal class PullWorker(
    private val transport: SyncTransport,
    private val cursorStore: CursorStore,
    private val database: AppDatabase,
    appliers: List<SyncApplier>,
    private val entityTypesOfInterest: Set<SyncEntityType>? = null,
    /**
     * Defaults to [NoOpSyncAuthProvider] so tests driving a fake transport need not supply one --
     * production wiring binds the real provider in `SyncDataModule`. With the no-op, a 401 simply
     * propagates, which is what a fake transport that never 401s wants anyway.
     */
    private val authProvider: SyncAuthProvider = NoOpSyncAuthProvider,
    private val pageLimit: Int = 500,
    private val onUnrecognizedEntityType: (String) -> Unit = {},
    private val onApplierSetChanged: (before: Set<String>, after: Set<String>) -> Unit = { _, _ -> },
    private val clock: Clock = Clock.System,
) {
    private val appliersByType = appliers.associateBy { it.entityType }

    suspend fun pullAll(since: Long): PullOutcome {
        var cursor = since
        var pages = 0
        var changes = 0

        while (true) {
            val response = fetchPage(cursor)

            if (response.changes.isNotEmpty()) {
                applyPage(response.changes)
                changes += response.changes.size
            }
            cursorStore.advanceTo(response.nextCursor)
            cursor = response.nextCursor
            pages += 1

            if (!response.hasMore) {
                cursorStore.recordPulledAt(clock.now())
                return PullOutcome(pages, changes)
            }
        }
    }

    /**
     * The normal call site. Resumes from the persisted cursor -- unless this client can now apply
     * an entity type it could not before, in which case the cursor is not a safe resume point and
     * the whole feed is re-pulled from zero.
     *
     * Rows for unbound entity types are skipped *and* stepped over by the cursor (see [applyPage]),
     * so an app update that adds an applier would otherwise never see the history it previously
     * discarded. Re-pulling is cheap and idempotent by construction (03 §4.3), and it happens once
     * per applier-set change, not once per sync.
     */
    suspend fun pullAll(): PullOutcome {
        val knownTypes = appliersByType.keys.map { it.name }.toSet()
        val previouslyKnown = cursorStore.readAppliedTypes()
        val healVersion = cursorStore.readHealVersion()

        // Subset, not inequality: only *gaining* the ability to apply a type invalidates the cursor.
        // Losing one (a downgrade) leaves no unread history behind, so re-pulling would be pure cost.
        val gainedAnApplier = !previouslyKnown.containsAll(knownTypes)
        val needsRepair = healVersion < PULL_HEAL_VERSION

        val since = if (gainedAnApplier || needsRepair) {
            onApplierSetChanged(previouslyKnown, knownTypes)
            cursorStore.reset()
            0L
        } else {
            cursorStore.observe().first()
        }

        // Written before the pull, not after: a crash mid-pull must not leave these stale and
        // trigger an identical reset on every subsequent launch. Losing one reset is harmless (the
        // next applier-set change re-triggers it); looping resets forever is not.
        if (knownTypes != previouslyKnown) cursorStore.writeAppliedTypes(knownTypes)
        if (needsRepair) cursorStore.writeHealVersion(PULL_HEAL_VERSION)

        return pullAll(since = since)
    }

    internal companion object {
        /**
         * Bump to force one full re-pull on every existing device.
         *
         * Version 2 repairs the devices that version 1's own guard could not reach: it only reset
         * the cursor when a *previously recorded* applier set had grown, so a device carrying no
         * record — every device that had synced before that key existed, which was the entire
         * population it was written for — took the else branch, resumed from its poisoned cursor,
         * and then recorded the current set, making itself permanently indistinguishable from a
         * healthy device.
         */
        const val PULL_HEAL_VERSION = 2
    }

    /**
     * One page fetch with the same 401 → refresh → retry-once policy `PushWorker` already applies
     * (03 §3.4). Pull had no such handling: a device holding an expired access token got a 401,
     * `SyncEngineImpl` recorded an error, and the next attempt was whatever the scheduler produced
     * next -- another 401, since nothing on the pull path ever refreshed the token. The device could
     * only recover if some *other* component happened to refresh on its own (the SSE listener does),
     * which is luck, not a policy.
     *
     * A single retry, not a loop: if a freshly-refreshed token is still rejected, the credential is
     * genuinely dead and hammering the endpoint would not help.
     */
    private suspend fun fetchPage(cursor: Long) = try {
        transport.pull(cursor, pageLimit, entityTypesOfInterest)
    } catch (unauthorized: SyncAuthException) {
        when (authProvider.refresh()) {
            SyncRefreshOutcome.REFRESHED -> transport.pull(cursor, pageLimit, entityTypesOfInterest)
            SyncRefreshOutcome.REJECTED -> throw unauthorized
            // Throttled or unreachable: the session is fine, this attempt just failed (#133).
            SyncRefreshOutcome.TRANSIENT -> throw SyncTransientException("refresh after 401 failed transiently", unauthorized)
        }
    }

    private suspend fun applyPage(changes: List<ChangeDto>) {
        database.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                for (change in changes) {
                    val entityType = SyncEntityType.entries.find { it.name == change.entityType }
                    val opType = SyncOpType.entries.find { it.name == change.opType }
                    val applier = entityType?.let { appliersByType[it] }
                    if (applier == null || opType == null) {
                        onUnrecognizedEntityType(change.entityType)
                        continue
                    }
                    applier.apply(change.payload, opType, change.serverSeq)
                }
            }
        }
    }
}
