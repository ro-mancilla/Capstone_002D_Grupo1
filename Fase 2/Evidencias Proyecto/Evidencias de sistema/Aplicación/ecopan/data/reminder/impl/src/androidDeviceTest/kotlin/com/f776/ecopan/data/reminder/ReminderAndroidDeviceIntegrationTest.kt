package com.f776.ecopan.data.reminder

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.f776.ecopan.data.reminder.android.boot.BootReceiver
import com.f776.ecopan.data.reminder.android.notification.ReminderBroadcastReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderAndroidDeviceIntegrationTest {

    @Test
    fun reminderManifest_declaresExpectedPermissions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val requestedPermissions = packageInfo.requestedPermissions?.toSet().orEmpty()

        assertTrue(requestedPermissions.contains(Manifest.permission.POST_NOTIFICATIONS))
        assertTrue(requestedPermissions.contains(Manifest.permission.RECEIVE_BOOT_COMPLETED))
        // Android 13+
        assertTrue(requestedPermissions.contains(Manifest.permission.USE_EXACT_ALARM))
    }

    @Test
    fun reminderManifest_declaresBootAndClassReceivers() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val packageManager = context.packageManager

        val bootReceiverInfo = packageManager.getReceiverInfo(
            ComponentName(context, BootReceiver::class.java),
            PackageManager.GET_META_DATA
        )
        assertNotNull(bootReceiverInfo)
        assertTrue(bootReceiverInfo.enabled)

        val reminderReceiverInfo = packageManager.getReceiverInfo(
            ComponentName(context, ReminderBroadcastReceiver::class.java),
            PackageManager.GET_META_DATA
        )
        assertNotNull(reminderReceiverInfo)
        assertTrue(reminderReceiverInfo.enabled)
    }

    @Test
    fun bootReceiver_hasBootCompletedIntentFilter() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val resolveInfo = context.packageManager.queryBroadcastReceivers(
            Intent(Intent.ACTION_BOOT_COMPLETED).setPackage(context.packageName),
            0
        )

        assertTrue(resolveInfo.isNotEmpty())
        assertEquals(
            BootReceiver::class.java.name,
            resolveInfo.first().activityInfo.name
        )
    }
}

