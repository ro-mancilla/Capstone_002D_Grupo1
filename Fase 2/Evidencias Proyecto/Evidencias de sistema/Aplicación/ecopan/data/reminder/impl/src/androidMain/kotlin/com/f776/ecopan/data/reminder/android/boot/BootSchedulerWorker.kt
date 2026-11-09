package com.f776.ecopan.data.reminder.android.boot

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

internal class BootSchedulerWorker(
    context: Context,
    params: WorkerParameters,
    private val bootScheduler: BootScheduler
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        bootScheduler.rescheduleAllEnabledReminders()
        return Result.success()
    }
}

