package com.f776.ecopan.domain.sync

/** [retriable] distinguishes a transient failure (backs off and retries) from a dead letter. */
data class SyncError(
    val message: String,
    val retriable: Boolean,
)
