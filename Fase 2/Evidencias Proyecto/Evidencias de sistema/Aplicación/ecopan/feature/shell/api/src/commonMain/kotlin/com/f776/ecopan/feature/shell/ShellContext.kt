package com.f776.ecopan.feature.shell

import com.f776.core.designsystem.component.SyncIndicatorState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Who is working where, right now. Every screen's header needs it and none of them should be
 * working it out: the "active branch is the first branch" rule (01 §4-style single-tenant device)
 * was stated in prose in one ViewModel and re-derived in two others.
 */
data class ShiftContext(
    val branchId: Uuid?,
    val userId: Uuid?,
    val tenantName: String,
    /** Needed to re-login: a cashier should not have to remember which bakery this till is. */
    val tenantSlug: String,
    /** The branch being worked ("Sucursal Principal"); blank until the bootstrap pull has cached it. */
    val branchName: String = "",
)

/**
 * The three questions every client screen asks that are not about its own subject: which branch,
 * which bakery, and how is sync doing (03 §6.1 — the indicator must be visible at all times).
 *
 * Both clients' screens used to answer them from `TenantRepository` + `SessionProvider` +
 * `SyncEngine` directly, three wide interfaces of which each used one or two members. The cost
 * showed in the fakes: seven near-identical stubs of the tenant interface and four of the sync
 * engine, differing only in which methods said "not needed by this test".
 *
 * This lives in the shared `:feature:*` tier on purpose. The mapping from `SyncStatus` to
 * `SyncIndicatorState` was previously duplicated because the only two candidate homes were
 * thought to be `:core:ui` (which must stay domain-free) and `:composeApp` (which the client
 * feature modules cannot depend on). A feature-tier module is the third option: it sits below
 * `:composeApp` and above `:core:ui`, and may depend on `:data:sync:api` — `:feature:auth:impl`
 * already does exactly that.
 */
interface ShellContext {
    val shift: Flow<ShiftContext>

    /** Already translated for the indicator, so no screen maps `SyncStatus` itself. */
    val indicator: Flow<SyncIndicatorState>

    /** When the device last completed a pull from the server; null if it never has. */
    val lastSyncedAt: Flow<Instant?> get() = flowOf(null)

    suspend fun syncNow()
}
