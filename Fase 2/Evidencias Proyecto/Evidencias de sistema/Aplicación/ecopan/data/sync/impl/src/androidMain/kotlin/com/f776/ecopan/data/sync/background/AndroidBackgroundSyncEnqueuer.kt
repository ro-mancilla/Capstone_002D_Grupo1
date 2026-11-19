package com.f776.ecopan.data.sync.background

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.f776.ecopan.domain.sync.BackgroundSyncEnqueuer

/**
 * Android actual for [BackgroundSyncEnqueuer] (03 §7). Enqueued from `MainActivity.onStop()`, i.e.
 * exactly when the foreground `SyncScheduler` loop is about to stop mattering.
 *
 * - `NetworkType.CONNECTED` — no point waking to push into an offline radio.
 * - `ExistingWorkPolicy.KEEP` with a fixed name — a cashier flipping between apps must not queue a
 *   pile of duplicate flushes; if one is already pending, that one stands.
 * - Expedited, with `RUN_AS_NON_EXPEDITED_WORK_REQUEST` as the quota fallback: unsynced sales are
 *   worth asking for a fast slot, but never worth failing to enqueue at all when the app has spent
 *   its expedited quota.
 */
internal class AndroidBackgroundSyncEnqueuer(private val context: Context) : BackgroundSyncEnqueuer {

    override fun enqueueOutboxFlush() {
        val request = OneTimeWorkRequestBuilder<OutboxFlushWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    private companion object {
        const val WORK_NAME = "ecopan-outbox-flush"
    }
}
