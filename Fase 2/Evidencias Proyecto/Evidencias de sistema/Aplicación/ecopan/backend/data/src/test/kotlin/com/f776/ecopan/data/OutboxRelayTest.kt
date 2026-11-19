package com.f776.ecopan.data

import com.f776.ecopan.data.database.repository.OutboxRepositoryImpl
import com.f776.ecopan.data.outbox.OutboxRelay
import com.f776.ecopan.data.outbox.OutboxTopic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Collections
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** #136: the shared transactional outbox and its relay, against a real Postgres. */
class OutboxRelayTest : RepositoryTestBase() {

    private val clock = TestClock()
    private val outbox = OutboxRepositoryImpl(clock)

    @BeforeEach
    fun setUp() = truncateAll()

    private fun enqueue(vararg payloads: String) = transaction {
        payloads.forEach { outbox.enqueue(OutboxTopic.PRODUCTION_BATCH, it) }
    }

    @Test
    fun `enqueue joins the caller's transaction and rolls back with it`() = runBlocking {
        assertFailsWith<IllegalStateException> {
            transaction {
                outbox.enqueue(OutboxTopic.PRODUCTION_BATCH, "doomed")
                error("the caller's own write failed")
            }
        }
        enqueue("kept")

        val published = mutableListOf<String>()
        OutboxRelay(outbox, mapOf(OutboxTopic.PRODUCTION_BATCH to { p: String -> published += p })).relayOnce()

        assertEquals(listOf("kept"), published)
    }

    @Test
    fun `the relay publishes pending rows once and marks them sent`() = runBlocking {
        enqueue("a", "b")
        val published = mutableListOf<String>()
        val relay = OutboxRelay(outbox, mapOf(OutboxTopic.PRODUCTION_BATCH to { p: String -> published += p }))

        assertEquals(2, relay.relayOnce())
        assertEquals(0, relay.relayOnce())
        assertEquals(listOf("a", "b"), published)
    }

    @Test
    fun `a failed publish is retried later with backoff, then sent`() = runBlocking {
        enqueue("flaky")
        var brokerUp = false
        val published = mutableListOf<String>()
        val relay = OutboxRelay(outbox, mapOf(OutboxTopic.PRODUCTION_BATCH to { p: String ->
            if (!brokerUp) throw java.io.IOException("broker down")
            published += p
        }))

        assertEquals(0, relay.relayOnce())
        brokerUp = true
        assertEquals(0, relay.relayOnce(), "not due again until its backoff has passed")
        clock.advance(1.minutes)
        assertEquals(1, relay.relayOnce())
        assertEquals(listOf("flaky"), published)
    }

    @Test
    fun `two relays over one table never publish a row twice`() {
        val rows = (1..300).map { "row-$it" }
        enqueue(*rows.toTypedArray())
        val published = Collections.synchronizedList(mutableListOf<String>())
        val publish: suspend (String) -> Unit = { p ->
            published += p
            delay(1) // hold the claim a moment, so the two relays genuinely overlap
        }
        val relays = List(2) { OutboxRelay(OutboxRepositoryImpl(clock), mapOf(OutboxTopic.PRODUCTION_BATCH to publish), batchSize = 20) }

        runBlocking(Dispatchers.IO) {
            relays.map { relay -> async { while (relay.relayOnce() > 0 || published.size < rows.size) Unit } }.awaitAll()
        }

        assertEquals(rows.size, published.size, "duplicates: ${published.groupBy { it }.filterValues { it.size > 1 }.keys}")
        assertEquals(rows.toSet(), published.toSet())
    }

    /** The app and the worker share the table: each relay must leave the other's topic alone. */
    @Test
    fun `a relay claims only the topics it publishes and never backs off the others`() = runBlocking {
        enqueue("batch")
        transaction { outbox.enqueue(OutboxTopic.BATCH_PROCESSED, "notice") }
        val appSent = mutableListOf<String>()
        val workerSent = mutableListOf<String>()

        assertEquals(1, OutboxRelay(outbox, mapOf(OutboxTopic.PRODUCTION_BATCH to { p: String -> appSent += p })).relayOnce())
        assertEquals(1, OutboxRelay(outbox, mapOf(OutboxTopic.BATCH_PROCESSED to { p: String -> workerSent += p })).relayOnce())

        assertEquals(listOf("batch"), appSent)
        assertEquals(listOf("notice"), workerSent)
    }

    /** The app creates the outbox (with its index) at boot; the worker's ensure must then be a no-op. */
    @Test
    fun `ensureOutbox on a database that already has the outbox succeeds`() = runBlocking {
        com.f776.ecopan.data.database.ensureOutbox(attempts = 1)
        com.f776.ecopan.data.database.ensureOutbox(attempts = 1)
    }

    @Test
    fun `the relay ticks about once a second by default`() {
        assertEquals(1.seconds, OutboxRelay(outbox, emptyMap()).interval)
    }
}
