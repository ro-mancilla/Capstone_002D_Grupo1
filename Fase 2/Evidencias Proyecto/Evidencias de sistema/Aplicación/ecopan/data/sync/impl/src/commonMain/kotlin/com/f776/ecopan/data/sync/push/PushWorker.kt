package com.f776.ecopan.data.sync.push

import com.f776.ecopan.data.sync.transport.SyncAuthException
import com.f776.ecopan.data.sync.transport.SyncRefreshOutcome
import com.f776.ecopan.data.sync.transport.SyncAuthProvider
import com.f776.ecopan.data.sync.transport.SyncTransientException
import com.f776.ecopan.data.sync.transport.SyncTransport
import com.f776.ecopan.domain.sync.wire.MutationDto
import com.f776.ecopan.domain.sync.wire.MutationResultDto
import com.f776.ecopan.domain.sync.wire.PushRequest
import com.f776.unigrid.core.database.dao.OutboxDao
import com.f776.unigrid.core.database.entity.OutboxEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

/** Phases reported mid-drain, so callers (`SyncEngineImpl`) can project them onto [com.f776.ecopan.domain.sync.SyncStatus]. */
internal enum class PushPhase { SYNCING, PENDING_BACKOFF }

internal sealed interface PushOutcome {
    /** Outbox fully drained (or was already empty). */
    data class Done(val pushed: Int) : PushOutcome

    /** Refresh failed after a 401 -- caller should surface `SyncStatus.AuthRequired` and stop. */
    data class AuthRequired(val pushedBeforeFailure: Int) : PushOutcome

    /**
     * Only returned when the caller capped the retries (`maxAttempts`): the transport kept failing
     * transiently, the batch is back in the queue, and the caller chose not to wait any longer.
     */
    data class Unreachable(val pushedBeforeFailure: Int) : PushOutcome
}

/**
 * Drains the outbox FIFO by rowid, in batches of [batchSize] (03 §3.1). One batch in flight at a
 * time; the whole drain loop keeps going batch-after-batch until the outbox is empty, retrying a
 * transient-failing batch in place with exponential backoff (03 §3.3) rather than giving up. Not
 * thread-safe / not re-entrant — callers (`SyncEngineImpl`/`SyncSchedulerImpl`, 03 §7) are
 * responsible for single-flighting this with a `Mutex`.
 */
internal class PushWorker(
    private val outboxDao: OutboxDao,
    private val transport: SyncTransport,
    private val authProvider: SyncAuthProvider,
    private val deviceIdProvider: suspend () -> String,
    private val batchSize: Int = 50,
    private val random: Random = Random.Default,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * @param maxAttempts caps consecutive transient failures; null (the default) retries forever.
     *   For a caller that must not wait on an unreachable server, e.g. `SyncEngineImpl.bootstrap`.
     */
    suspend fun drainAll(maxAttempts: Int? = null, onPhase: suspend (PushPhase) -> Unit = {}): PushOutcome {
        var pushed = 0
        var backoffAttempt = 0

        while (true) {
            val batch = outboxDao.takeBatch(batchSize)
            if (batch.isEmpty()) return PushOutcome.Done(pushed)

            onPhase(PushPhase.SYNCING)
            val mutationIds = batch.map { it.mutationId }
            outboxDao.markInFlight(mutationIds)

            val request = PushRequest(deviceId = deviceIdProvider(), mutations = batch.map { it.toMutationDto() })

            val response = try {
                pushAuthenticated(request)
            } catch (unauthorized: SyncAuthException) {
                outboxDao.revertToPending(mutationIds)
                return PushOutcome.AuthRequired(pushed)
            } catch (transient: SyncTransientException) {
                outboxDao.revertToPending(mutationIds)
                outboxDao.incrementAttempts(mutationIds)
                backoffAttempt += 1
                if (maxAttempts != null && backoffAttempt >= maxAttempts) return PushOutcome.Unreachable(pushed)
                onPhase(PushPhase.PENDING_BACKOFF)
                delay(backoffDelayMillis(backoffAttempt, random).milliseconds)
                continue
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (unexpected: Exception) {
                // Every *anticipated* failure above puts the batch back in the queue; anything else
                // -- a malformed payload, a serialization bug, a transport that throws something
                // undeclared -- used to escape with the batch still marked IN_FLIGHT. `takeBatch`
                // only reads PENDING, so those rows went quiet for the rest of the process: the
                // engine reported a retriable error, every later sync found "nothing to push", and
                // the pill settled on synced. They came back only on the next launch, via the
                // crash-recovery sweep in `SyncEngineImpl`'s init. Unwind the mark here so the very
                // next attempt sees them.
                outboxDao.revertToPending(mutationIds)
                throw unexpected
            }

            backoffAttempt = 0
            pushed += try {
                applyResults(batch, response.results)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (unexpected: Exception) {
                // Same reasoning one step later: a DAO failure part-way through result application
                // would otherwise strand whatever was still IN_FLIGHT. Rows already deleted as
                // acked are simply not matched by this update.
                outboxDao.revertToPending(mutationIds)
                throw unexpected
            }
        }
    }

    private suspend fun pushOnce(request: PushRequest) = transport.push(request)

    /**
     * One push with 401 → refresh → retry-once (03 §3.4). Throws [SyncAuthException] only when the
     * session is really dead: the refresh was rejected, or a freshly refreshed token is refused too.
     * A throttled or unreachable refresh is a [SyncTransientException], retried with backoff like any
     * other transient failure instead of parking the outbox behind "Requiere atención" (#133).
     */
    private suspend fun pushAuthenticated(request: PushRequest) = try {
        pushOnce(request)
    } catch (unauthorized: SyncAuthException) {
        when (authProvider.refresh()) {
            SyncRefreshOutcome.REFRESHED -> pushOnce(request)
            SyncRefreshOutcome.REJECTED -> throw unauthorized
            SyncRefreshOutcome.TRANSIENT -> throw SyncTransientException("refresh after 401 failed transiently", unauthorized)
        }
    }

    private suspend fun applyResults(
        batch: List<OutboxEntity>,
        results: List<MutationResultDto>,
    ): Int {
        val byMutationId = results.associateBy { it.mutationId }
        val acked = mutableListOf<Uuid>()
        var appliedCount = 0

        for (row in batch) {
            when (byMutationId[row.mutationId.toString()]?.status) {
                "APPLIED", "DUPLICATE" -> {
                    acked += row.mutationId
                    appliedCount += 1
                }
                "REJECTED" -> {
                    val error = byMutationId[row.mutationId.toString()]?.error
                    outboxDao.markFailed(row.mutationId, error?.message ?: error?.code ?: "rejected")
                }
                else -> {
                    // Server didn't return a result for this row at all -- treat conservatively as
                    // not-yet-acked so it's retried, rather than silently losing the mutation.
                    outboxDao.revertToPending(listOf(row.mutationId))
                }
            }
        }

        if (acked.isNotEmpty()) outboxDao.deleteAcked(acked)
        return appliedCount
    }

    private fun OutboxEntity.toMutationDto() = MutationDto(
        mutationId = mutationId.toString(),
        entityType = entityType,
        opType = opType,
        clientTimestamp = createdAt.toEpochMilliseconds(),
        payload = json.parseToJsonElement(payload),
    )
}

/** 5s · 2^n, cap 5 min, ±20% jitter (03 §3.3). [attempt] is 1-based. */
internal fun backoffDelayMillis(attempt: Int, random: Random = Random.Default): Long {
    val capped = min(5_000.0 * 2.0.pow(attempt - 1), 300_000.0)
    val jitterFactor = 1.0 + (random.nextDouble(-0.2, 0.2))
    return (capped * jitterFactor).toLong().coerceAtLeast(0L)
}
