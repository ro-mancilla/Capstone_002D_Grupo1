package com.f776.ecopan

import android.app.AlarmManager
import android.app.Application
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.f776.ecopan.data.reminder.android.boot.ExactAlarmPermissionStateChangedBroadcastReceiver
import com.f776.ecopan.data.reminder.android.notification.AppNotificationChannel
import com.f776.ecopan.di.initKoin
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.koin.workManagerFactory

class EcopanApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        initKoin {
            androidContext(this@EcopanApplication)
            workManagerFactory()
        }

        val notificationChannel by inject<AppNotificationChannel>()
        notificationChannel.createNotificationChannel()

        // Register receiver only for 31 and 32
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.S..Build.VERSION_CODES.S_V2) {
            Log.d(
                "EcopanApplication",
                "Registering ExactAlarmPermissionStateChangedBroadcastReceiver"
            )
            val receiver = ExactAlarmPermissionStateChangedBroadcastReceiver()
            val filter = IntentFilter(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)
            ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }
    }
}