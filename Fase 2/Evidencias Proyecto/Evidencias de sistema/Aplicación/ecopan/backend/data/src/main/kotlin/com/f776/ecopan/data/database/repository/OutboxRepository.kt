package com.f776.ecopan.data.database.repository

import com.f776.ecopan.data.outbox.OutboxTopic

/** One row of the outbox, as the relay sees it. */
data class OutboxMessage(val topic: OutboxTopic, val payload: String, val attempts: Int)

interface OutboxRepository {
    /**
     * Writes a message in the CALLER's transaction: call it inside the `transaction {}` /
     * `suspendTransaction {}` that makes the state change, so the two commit or roll back together.
     * Throws if there is no transaction in scope rather than silently committing on its own.
     */
    fun enqueue(topic: OutboxTopic, payload: String)

    /**
     * Claims up to [limit] due rows of [topics] -- only the ones this process can publish; the app
     * and the worker share the table, and claiming the other's rows would fail and back them off (`FOR UPDATE SKIP LOCKED`, so a second relay skips them), hands
     * each to [publish] while holding the claim, and marks it sent -- or, if [publish] throws, puts it
     * back with a backoff. Returns how many were published.
     */
    suspend fun relayDue(limit: Int, topics: Set<OutboxTopic> = OutboxTopic.entries.toSet(), publish: suspend (OutboxMessage) -> Unit): Int
}
