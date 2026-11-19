package com.f776.ecopan.domain.sync

/**
 * Wire entity types shared by `POST /sync/push` and `GET /sync/pull` (02 §4.1/§4.2), mirrored
 * 1:1 against the backend's own `com.f776.ecopan.data.domain.SyncEntityType` (`backend/data`) so
 * that `entityType` strings round-trip exactly. Deliberately does **not** include `SALE_LINE` —
 * sale lines are embedded in the `SALE` payload, never pushed/pulled standalone. `SALE` never
 * actually appears in a *pull* feed in practice (03 §4.2: "Sales history is not pulled to other
 * clients" — POS is origin, dashboard reads server-side) but the enum still carries it for the
 * push side and for symmetry with the backend type.
 *
 * `BRANCH`/`USER`/`PRODUCT`/`INGREDIENT`/`FORMULA` are pull-only config types
 * (03 §4.3 family 1); a client that doesn't recognize one of these (or gets a name the backend
 * added later) skips it rather than crashing — see `PullWorker`'s dispatch.
 */
enum class SyncEntityType {
    BRANCH,
    USER,
    PRODUCT,
    INGREDIENT,
    FORMULA,
    SALE,
    PRODUCTION_BATCH,
    STOCK_MOVEMENT,

    /** Pull-only, branch-scoped: the baker's cached *producción sugerida* (#52). */
    FORECAST,
}

/**
 * Facts (everything synced) are append-only — `CREATE` is the only op that ever appears on the
 * push wire (02 §4.1). `UPSERT` exists only in the *pull* feed, for server-authoritative config
 * rows (01 §6.2); clients never push an `UPSERT`.
 */
enum class SyncOpType { CREATE, UPSERT }
