package com.f776.ecopan.data.database.repository

import com.f776.ecopan.data.database.entity.ScheduledRunTable
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.time.Duration

internal class ScheduledRunRepositoryImpl(private val clock: Clock = Clock.System) : ScheduledRunRepository {

    override suspend fun tryClaim(job: String, day: LocalDate, scope: String, lease: Duration): ClaimResult =
        suspendTransaction {
            val now = clock.now()
            // ON CONFLICT DO NOTHING: the loser's insert affects zero rows rather than throwing.
            val inserted = ScheduledRunTable.insertIgnore {
                it[ScheduledRunTable.job] = job
                it[runDate] = day
                it[ScheduledRunTable.scope] = scope
                it[claimedAt] = now
            }.insertedCount == 1
            if (inserted) return@suspendTransaction ClaimResult.WON

            // Lease takeover. The UPDATE re-checks the lease predicate under the row lock, so of
            // several takers only the first matches; the rest see the refreshed claimed_at.
            val tookOver = ScheduledRunTable.update({
                sameKey(job, day, scope) and
                    ScheduledRunTable.completedAt.isNull() and
                    (ScheduledRunTable.claimedAt less now - lease)
            }) { it[claimedAt] = now } > 0
            if (tookOver) return@suspendTransaction ClaimResult.WON

            val completed = ScheduledRunTable.selectAll()
                .where { sameKey(job, day, scope) }
                .singleOrNull()
                ?.get(ScheduledRunTable.completedAt) != null
            if (completed) ClaimResult.DONE else ClaimResult.HELD
        }

    override suspend fun complete(job: String, day: LocalDate, scope: String) {
        suspendTransaction {
            ScheduledRunTable.update({ sameKey(job, day, scope) }) { it[completedAt] = clock.now() }
        }
    }

    override suspend fun release(job: String, day: LocalDate, scope: String) {
        suspendTransaction {
            ScheduledRunTable.deleteWhere {
                sameKey(job, day, scope) and ScheduledRunTable.completedAt.isNull()
            }
        }
    }

    private fun sameKey(job: String, day: LocalDate, scope: String) =
        (ScheduledRunTable.job eq job) and (ScheduledRunTable.runDate eq day) and (ScheduledRunTable.scope eq scope)
}
