package com.f776.ecopan.data.database.entity

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.timestamp

/**
 * One row per (job, local day, scope) that a replica has claimed -- scope is e.g. a branch id, or
 * `""` for a job that runs once as a whole. The composite primary key is the lock: with N replicas
 * ticking, exactly one `INSERT ... ON CONFLICT DO NOTHING` wins. A claim is not a completion:
 * [completedAt] stays null until the work is done, so a replica killed mid-job leaves a claim
 * whose [claimedAt] lease expires and can be taken over, instead of losing the day.
 * Infra table -- no `server_seq`, never synced.
 */
object ScheduledRunTable : Table("scheduled_runs") {
    val job = varchar("job", 64)
    val runDate = date("run_date")
    val scope = varchar("scope", 64)
    val claimedAt = timestamp("claimed_at")
    val completedAt = timestamp("completed_at").nullable()

    override val primaryKey = PrimaryKey(job, runDate, scope)
}
