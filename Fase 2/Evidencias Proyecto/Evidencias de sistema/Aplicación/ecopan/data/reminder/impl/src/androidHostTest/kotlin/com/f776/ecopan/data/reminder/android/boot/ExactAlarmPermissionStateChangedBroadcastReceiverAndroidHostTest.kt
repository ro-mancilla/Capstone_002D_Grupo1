package com.f776.ecopan.data.reminder.android.boot

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class ExactAlarmPermissionStateChangedBroadcastReceiverAndroidHostTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().build()
        )
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.S])
    fun onReceive_wrongAction_doesNotEnqueueBootSchedulerWorker() {
        val receiver = ExactAlarmPermissionStateChangedBroadcastReceiver()

        receiver.onReceive(context, Intent(Intent.ACTION_TIME_CHANGED))

        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosByTag(BootSchedulerWorker::class.java.name)
            .get()

        assertEquals(0, workInfos.size)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun onReceive_exactAlarmAction_preAndroid12_doesNotEnqueueBootSchedulerWorker() {
        val receiver = ExactAlarmPermissionStateChangedBroadcastReceiver()

        receiver.onReceive(
            context,
            Intent(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)
        )

        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosByTag(BootSchedulerWorker::class.java.name)
            .get()

        assertEquals(0, workInfos.size)
    }
}

