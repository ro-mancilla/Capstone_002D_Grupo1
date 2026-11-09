package com.f776.ecopan.data.reminder.android.boot

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import org.koin.core.component.KoinComponent

/**
 * Simple broadcast receiver that listens to [AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED]
 * intent action.
 */
class ExactAlarmPermissionStateChangedBroadcastReceiver : BroadcastReceiver(),
    KoinComponent {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(
            "ExactAlarmPermissionStateChangedBroadcastReceiver",
            "SCHEDULE_EXACT_ALARM permission state changed"
        )
        if (intent.action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) {
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
                Log.d(
                    "ExactAlarmPermissionStateChangedBroadcastReceiver",
                    "Rescheduling alarms"
                )
                val workManager = WorkManager.getInstance(context)
                val immediateRequest = OneTimeWorkRequestBuilder<BootSchedulerWorker>()
                workManager.enqueue(immediateRequest.build())
            }
        }
    }
}
