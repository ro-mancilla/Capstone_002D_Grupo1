package com.f776.ecopan.domain.production

/** Read-model only — derived by joining the batch id against outbox state (06 §6), never stored. */
enum class BatchSyncState {
    PENDING,
    SYNCED,

    /**
     * The server refused this batch outright (a `REJECTED` push result), so it will never be
     * retried and the bakehouse's copy is the only copy.
     *
     * Without this the join asked one question — "is the id still waiting in the outbox?" — and
     * read "no" for a rejected row exactly as it does for an accepted one, so a batch the server
     * had thrown away displayed as *Sincronizado*. A baker checking whether the morning's work
     * registered was being told yes.
     */
    REJECTED,
}
