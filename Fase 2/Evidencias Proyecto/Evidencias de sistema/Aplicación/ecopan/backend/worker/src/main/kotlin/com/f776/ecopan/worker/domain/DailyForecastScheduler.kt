package com.f776.ecopan.worker.domain

import com.f776.ecopan.data.database.repository.BranchRepository
import com.f776.ecopan.data.database.repository.ClaimResult
import com.f776.ecopan.data.database.repository.ScheduledRunRepository
import com.f776.ecopan.data.database.repository.TenantRepository
import com.f776.ecopan.data.domain.Module
import com.f776.ecopan.data.messagebroker.ForecastRefreshBroker
import com.f776.ecopan.data.messagebroker.model.ForecastRefreshRequested
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toKotlinLocalDate
import org.slf4j.LoggerFactory
import java.time.LocalTime
import java.time.ZoneId
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Publishes the existing [ForecastRefreshRequested] for every active branch of every
 * `DEMAND_FORECASTING` tenant, once per reporting-timezone day, shortly after local midnight.
 *
 * **Once across replicas, without losing the day.** Every worker replica ticks this. For each
 * branch a replica must win a claim row (`scheduled_runs`: job, local day, branch) before
 * publishing, and marks it complete afterwards. A claim is a lease, not a completion: a replica
 * killed between claiming and publishing leaves an uncompleted row whose lease expires, and the
 * next tick on any replica takes it over. A publish failure releases the claim (best effort --
 * if even that fails the lease expiry covers it). Because the unit is the branch, a partial
 * failure never re-publishes branches that already went out.
 *
 * The window is "from [runAfter] onward", not "at [runAfter]", so a worker that was down at 00:05
 * still refreshes when it comes back the same day, and the day Santiago springs forward (no
 * 00:00-00:59) still fires, at 01:00.
 *
 * **One-shot, not a retry loop.** This only *publishes the request*. Whether the refresh succeeds
 * is up to the consumer ([ForecastRefreshProcessorImpl]): if the ML service is down at consume
 * time, that branch's message fails (RabbitMQ: nack without requeue and no dead-letter exchange on
 * the forecast queue, so it is dropped; Service Bus: dead-lettered) and the branch waits for
 * tomorrow's run or the GM's *Actualizar pronóstico* button.
 */
class DailyForecastScheduler(
    private val tenants: TenantRepository,
    private val branches: BranchRepository,
    private val runs: ScheduledRunRepository,
    private val broker: ForecastRefreshBroker,
    reportingTz: String,
    private val runAfter: LocalTime = LocalTime.of(0, 5),
    private val lease: Duration = 5.minutes,
    private val clock: Clock = Clock.System,
) {
    private val zone = ZoneId.of(reportingTz)

    /** Local day for which this instance has seen every branch complete; skips the DB until tomorrow. */
    @Volatile
    private var doneDay: LocalDate? = null

    /** Returns how many refresh requests this call published. Throws the first publish failure after trying every branch. */
    suspend fun tick(): Int {
        val local = java.time.Instant.ofEpochMilli(clock.now().toEpochMilliseconds()).atZone(zone)
        if (local.toLocalTime() < runAfter) return 0
        val day = local.toLocalDate().toKotlinLocalDate()
        if (doneDay == day) return 0

        var published = 0
        var settled = true
        var seen = 0
        var failure: Throwable? = null

        val eligible = try {
            tenants.listAll()
        } catch (e: Exception) {
            // Fresh stack: the app owns schema init and may not have created `tenants` yet. That is
            // a state, not a failure -- say so quietly and try again next tick.
            if (e.hasSqlState(UNDEFINED_TABLE)) {
                logger.warn("Waiting for the database schema (relation missing); forecast scheduler retries next tick")
                return 0
            }
            throw e
        }
        eligible
            .filter { Module.DEMAND_FORECASTING in it.enabledModules }
            .forEach { tenant ->
                branches.listByTenant(tenant.id).forEach { branch ->
                    val scope = branch.id.toString()
                    seen++
                    when (runs.tryClaim(JOB, day, scope, lease)) {
                        ClaimResult.DONE -> Unit
                        ClaimResult.HELD -> settled = false
                        ClaimResult.WON -> {
                            try {
                                broker.publish(
                                    ForecastRefreshRequested(tenantId = tenant.id.toString(), branchId = scope)
                                )
                                published++
                                runs.complete(JOB, day, scope)
                            } catch (e: Throwable) {
                                settled = false
                                releaseQuietly(day, scope)
                                val first = failure
                                if (first == null) failure = e else first.addSuppressed(e)
                            }
                        }
                    }
                }
            }

        if (published > 0) logger.info("Daily forecast refresh for {} requested for {} branch(es)", day, published)
        failure?.let { throw it }
        // Not vacuously: with no eligible branch yet, a tenant enabling forecasting later today must still be picked up.
        if (settled && seen > 0) doneDay = day
        return published
    }

    /** A failed release must never mask the publish error that got us here; the lease expiry is the backstop. */
    private suspend fun releaseQuietly(day: LocalDate, scope: String) {
        try {
            withContext(NonCancellable) { runs.release(JOB, day, scope) }
        } catch (e: Exception) {
            logger.warn("Could not release forecast-refresh claim {} {}; it will be retaken when its lease expires", day, scope, e)
        }
    }

    private fun Throwable.hasSqlState(state: String): Boolean =
        generateSequence(this) { it.cause }.any { (it as? java.sql.SQLException)?.sqlState == state }

    companion object {
        private const val UNDEFINED_TABLE = "42P01"
        const val JOB = "forecast-refresh"
        private val logger = LoggerFactory.getLogger(DailyForecastScheduler::class.java)
    }
}
