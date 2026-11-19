package com.f776.ecopan.data.messagebroker

import com.f776.ecopan.data.messagebroker.model.BatchProcessed

/**
 * The worker->notifier hop: per-batch completion events on a durable, acked queue consumed by the
 * notifier as competing consumers (at-least-once, work distribution).
 */
interface BatchEventBroker {
    suspend fun publishProcessed(event: BatchProcessed)
    suspend fun consumeProcessed(block: suspend (BatchProcessed) -> Unit)
}
