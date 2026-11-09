package com.f776.ecopan.data.sync.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.f776.ecopan.domain.sync.SyncEngine
import com.f776.unigrid.core.database.dao.OutboxDao
import kotlinx.coroutines.flow.first

/**
 * Best-effort background outbox flush (03 §7 Android hardening). Mirrors `:data:reminder:impl`'s
 * `BootSchedulerWorker` — same `CoroutineWorker` + Koin `workerOf` shape.
 *
 * Retries only when there is still something to send: a failed sync with an empty outbox means the
 * *pull* half failed, and re-running the whole cycle to retry a pull the foreground scheduler will
 * redo anyway is not worth a wakeup. Returning `success` on an empty outbox also stops WorkManager
 * from retrying forever on a device that simply has nothing to say.
 */
internal class OutboxFlushWorker(
    context: Context,
    params: WorkerParameters,
    private val syncEngine: SyncEngine,
    private val outboxDao: OutboxDao,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (outboxDao.observePendingCount().first() == 0) return Result.success()

        val result = syncEngine.syncNow()
        val stillPending = outboxDao.observePendingCount().first()
        return when {
            result.error == null -> Result.success()
            stillPending > 0 -> Result.retry()
            else -> Result.success()
        }
    }
}
