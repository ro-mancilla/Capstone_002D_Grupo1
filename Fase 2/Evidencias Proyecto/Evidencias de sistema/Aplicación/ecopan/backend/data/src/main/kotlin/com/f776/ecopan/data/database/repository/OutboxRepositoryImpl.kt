package com.f776.ecopan.data.database.repository

import com.f776.ecopan.data.database.entity.OutboxTable
import com.f776.ecopan.data.outbox.OutboxTopic
import kotlinx.coroutines.CancellationException
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

private val log = LoggerFactory.getLogger("com.f776.ecopan.data.outbox")

internal class OutboxRepositoryImpl(private val clock: Clock) : OutboxRepository {

    override fun enqueue(topic: OutboxTopic, payload: String) {
        checkNotNull(TransactionManager.currentOrNull()) { "outbox enqueue must run inside the caller's transaction" }
        val now = clock.now()
        OutboxTable.insert {
            it[id] = Uuid.random()
            it[OutboxTable.topic] = topic.name
            it[OutboxTable.payload] = payload
            it[createdAt] = now
            it[availableAt] = now
        }
    }

    override suspend fun relayDue(limit: Int, topics: Set<OutboxTopic>, publish: suspend (OutboxMessage) -> Unit): Int = suspendTransaction {
        val now = clock.now()
        // The claim lasts as long as this transaction: a second relay's SKIP LOCKED passes over
        // these rows instead of waiting for them or publishing them again.
        val due = OutboxTable.selectAll()
            .where {
                OutboxTable.sentAt.isNull() and (OutboxTable.availableAt lessEq now) and
                    (OutboxTable.topic inList topics.map { it.name })
            }
            .orderBy(OutboxTable.createdAt to SortOrder.ASC)
            .limit(limit)
            .forUpdate(ForUpdateOption.PostgreSQL.ForUpdate(ForUpdateOption.PostgreSQL.MODE.SKIP_LOCKED))
            .toList()

        var published = 0
        for (row in due) {
            val id = row[OutboxTable.id]
            val attempts = row[OutboxTable.attempts]
            val topic = OutboxTopic.entries.firstOrNull { it.name == row[OutboxTable.topic] }
            try {
                checkNotNull(topic) { "unknown outbox topic ${row[OutboxTable.topic]}" }
                publish(OutboxMessage(topic, row[OutboxTable.payload], attempts))
                OutboxTable.update({ OutboxTable.id eq id }) { it[sentAt] = clock.now() }
                published += 1
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val wait = backoff(attempts + 1)
                log.warn("outbox {} ({}) publish failed, attempt {}; retrying in {}", id, row[OutboxTable.topic], attempts + 1, wait, e)
                OutboxTable.update({ OutboxTable.id eq id }) {
                    it[OutboxTable.attempts] = attempts + 1
                    it[availableAt] = now + wait
                    it[lastError] = e.message?.take(500)
                }
            }
        }
        published
    }

    /** 2 s, 4 s, 8 s ... capped at 5 minutes: a broker outage costs a few rows a minute, not a spin. */
    private fun backoff(attempt: Int): Duration =
        (2.seconds * (1L shl (attempt - 1).coerceIn(0, 16)).toInt()).coerceAtMost(5.minutes)
}
