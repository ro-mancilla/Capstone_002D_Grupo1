package com.f776.ecopan.data.reminder.android.boot

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BootReceiverAndroidHostTest {

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
    fun onReceive_bootCompleted_enqueuesBootSchedulerWorker() {
        val receiver = BootReceiver()

        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosByTag(BootSchedulerWorker::class.java.name)
            .get()

        assertEquals(1, workInfos.size)
    }

    @Test
    fun bootReceiver_isExported_soTheSystemCanActuallyDeliverBootCompleted() {
        // The two tests around this one call onReceive directly, so they passed for a long time
        // while the real delivery path was dead: the receiver was declared exported="false", and
        // BOOT_COMPLETED comes from the system uid, which a non-exported manifest receiver cannot
        // hear. Every alarm was silently lost on reboot.
        val receivers = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_RECEIVERS)
            .receivers
            .orEmpty()

        val bootReceiver = receivers.singleOrNull {
            it.name == BootReceiver::class.java.name
        }

        assertNotNull("BootReceiver is not declared in the manifest", bootReceiver)
        assertTrue(
            "BootReceiver must be exported or the system cannot deliver BOOT_COMPLETED to it",
            requireNotNull(bootReceiver).exported
        )
    }

    @Test
    fun onReceive_otherAction_doesNotEnqueueBootSchedulerWorker() {
        val receiver = BootReceiver()

        receiver.onReceive(context, Intent(Intent.ACTION_PACKAGE_ADDED))

        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosByTag(BootSchedulerWorker::class.java.name)
            .get()

        assertEquals(0, workInfos.size)
    }
}

