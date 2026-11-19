package com.f776.ecopan.data

import com.f776.ecopan.data.database.ensureScheduledRuns
import com.f776.ecopan.data.database.repository.ClaimResult
import com.f776.ecopan.data.database.repository.ScheduledRunRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Once-across-replicas claim behind the daily forecast refresh. The unique key is the lock; the
 * lease + `completed_at` is what stops a worker killed mid-job from losing the day.
 */
class ScheduledRunRepositoryTest : RepositoryTestBase() {

    private val runs = ScheduledRunRepositoryImpl()
    private val job = "forecast-refresh"
    private val day = LocalDate(2026, 7, 20)
    private val lease = 5.minutes

    @BeforeEach
    fun setUp() = truncateAll()

    @Test
    fun `second claim of a live lease is held, not won`() = runBlocking {
        assertEquals(ClaimResult.WON, runs.tryClaim(job, day, "b1", lease))
        assertEquals(ClaimResult.HELD, runs.tryClaim(job, day, "b1", lease))
    }

    @Test
    fun `a completed scope reports DONE and is never taken over`() = runBlocking {
        runs.tryClaim(job, day, "b1", lease)
        runs.complete(job, day, "b1")
        assertEquals(ClaimResult.DONE, runs.tryClaim(job, day, "b1", lease))
        assertEquals(ClaimResult.DONE, runs.tryClaim(job, day, "b1", 0.seconds))
    }

    @Test
    fun `an expired lease on an uncompleted claim is taken over, once`() = runBlocking {
        runs.tryClaim(job, day, "b1", lease) // the worker that then dies
        assertEquals(ClaimResult.WON, runs.tryClaim(job, day, "b1", 0.seconds))
        // The takeover refreshed claimed_at, so a third caller with a real lease sees it held.
        assertEquals(ClaimResult.HELD, runs.tryClaim(job, day, "b1", 1.hours))
    }

    @Test
    fun `scopes, days and jobs are independent`() = runBlocking {
        assertEquals(ClaimResult.WON, runs.tryClaim(job, day, "b1", lease))
        assertEquals(ClaimResult.WON, runs.tryClaim(job, day, "b2", lease))
        assertEquals(ClaimResult.WON, runs.tryClaim(job, LocalDate(2026, 7, 21), "b1", lease))
        assertEquals(ClaimResult.WON, runs.tryClaim("other", day, "b1", lease))
    }

    @Test
    fun `release lets an uncompleted claim be won again but cannot undo a completion`() = runBlocking {
        runs.tryClaim(job, day, "b1", lease)
        runs.release(job, day, "b1")
        assertEquals(ClaimResult.WON, runs.tryClaim(job, day, "b1", lease))
        runs.complete(job, day, "b1")
        runs.release(job, day, "b1")
        assertEquals(ClaimResult.DONE, runs.tryClaim(job, day, "b1", lease))
    }

    @Test
    fun `many racing claimers produce exactly one winner, also across a lease takeover`() = runBlocking {
        val fresh = (1..16).map {
            async(Dispatchers.IO) { ScheduledRunRepositoryImpl().tryClaim(job, day, "b1", lease) }
        }.awaitAll()
        assertEquals(1, fresh.count { it == ClaimResult.WON })

        val takeover = (1..16).map {
            async(Dispatchers.IO) { ScheduledRunRepositoryImpl().tryClaim(job, day, "b1", 0.seconds) }
        }.awaitAll()
        // Zero lease means every attempt may legitimately take over after the previous one's
        // claimed_at; the invariant is that no attempt ever sees DONE and at least one wins.
        assertEquals(true, takeover.any { it == ClaimResult.WON })
        assertEquals(false, takeover.any { it == ClaimResult.DONE })
    }

    @Test
    fun `ensureScheduledRuns creates a missing table and is idempotent`() = runBlocking {
        // A worker can boot before the app has run its schema init.
        suspendTransaction { exec("DROP TABLE IF EXISTS scheduled_runs") }
        ensureScheduledRuns()
        ensureScheduledRuns()
        assertEquals(ClaimResult.WON, runs.tryClaim(job, day, "b1", lease))
    }
}
