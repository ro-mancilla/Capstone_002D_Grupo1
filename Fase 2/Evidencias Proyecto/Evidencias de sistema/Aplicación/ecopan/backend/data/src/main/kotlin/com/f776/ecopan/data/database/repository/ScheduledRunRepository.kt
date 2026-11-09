package com.f776.ecopan.data.database.repository

import kotlinx.datetime.LocalDate
import kotlin.time.Duration

enum class ClaimResult {
    /** This caller owns the work and must [ScheduledRunRepository.complete] or release it. */
    WON,

    /** Another replica holds a live lease; it may still finish, or die and let the lease expire. */
    HELD,

    /** The work already completed for this day. */
    DONE,
}

/**
 * Claim-once-per-day bookkeeping for scheduled jobs that must not fire on every replica, and must
 * not be lost when the replica that claimed them dies (claim, work, then [complete]).
 */
interface ScheduledRunRepository {
    /**
     * Atomically claims ([job], [day], [scope]). Takes over an uncompleted claim whose lease
     * ([lease] since it was claimed) has expired.
     */
    suspend fun tryClaim(job: String, day: LocalDate, scope: String = "", lease: Duration): ClaimResult

    suspend fun complete(job: String, day: LocalDate, scope: String = "")

    /** Gives an uncompleted claim back so the next tick can retry immediately. No-op once completed. */
    suspend fun release(job: String, day: LocalDate, scope: String = "")
}
