package com.f776.ecopan.data.reminder.android.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import org.koin.core.component.KoinComponent

internal class BootReceiver : BroadcastReceiver(), KoinComponent {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val workManager = WorkManager.getInstance(context)
            val immediateRequest = OneTimeWorkRequestBuilder<BootSchedulerWorker>()
            workManager.enqueue(immediateRequest.build())
        }
    }
}