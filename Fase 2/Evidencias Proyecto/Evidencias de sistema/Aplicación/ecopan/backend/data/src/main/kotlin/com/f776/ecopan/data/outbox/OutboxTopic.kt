package com.f776.ecopan.data.outbox

/**
 * What an outbox row is, and so which broker client publishes it. Each process runs a relay for
 * the topics it writes: the app for [PRODUCTION_BATCH], the worker for [BATCH_PROCESSED].
 */
enum class OutboxTopic {
    /** App -> worker: `ProductionBatchMessage`, written with the batch it describes. */
    PRODUCTION_BATCH,

    /** Worker -> notifier: `BatchProcessed`, written with the decrement it reports. */
    BATCH_PROCESSED,
}
