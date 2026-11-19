package com.f776.ecopan.data.reminder.permission

import android.app.Application
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import dev.icerock.moko.permissions.PermissionsController
import dev.shreyaspatil.permissionFlow.PermissionFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
class ReminderPermissionManagerImplAndroidHostTest {

    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun hasAlarmPermission_preAndroid12_isAlwaysTrue() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = createManager(context)

        assertTrue(manager.hasAlarmPermission().first())
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun hasNotificationPermission_preAndroid13_isAlwaysTrue() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = createManager(context)

        assertTrue(manager.hasNotificationPermission().first())
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.S])
    fun requestAlarmPermission_whenExactAlarmNotGranted_opensSystemSettings() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = createManager(context)

        manager.requestAlarmPermission()

        val startedIntent = shadowOf(context as Application).nextStartedActivity
        assertNotNull(startedIntent)
        assertEquals(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, startedIntent.action)
    }

    private fun createManager(context: Context): ReminderPermissionManagerImpl {
        PermissionFlow.init(context)

        val permissionsController = Proxy.newProxyInstance(
            PermissionsController::class.java.classLoader,
            arrayOf(PermissionsController::class.java)
        ) { _, method, _ ->
            when (method.returnType) {
                java.lang.Boolean.TYPE -> false
                Int::class.javaPrimitiveType -> 0
                java.lang.Long.TYPE -> 0L
                java.lang.Float.TYPE -> 0f
                java.lang.Double.TYPE -> 0.0
                Void.TYPE -> Unit
                else -> null
            }
        } as PermissionsController

        return ReminderPermissionManagerImpl(
            context = context,
            permissionsController = permissionsController,
            toaster = com.f776.ecopan.data.reminder.utils.Toaster(context),
            ioDispatcher = Dispatchers.Unconfined,
            // Only used to hop onto the main thread for a toast; under Robolectric the
            // unconfined dispatcher keeps that inline so no test has to pump a looper.
            mainDispatcher = Dispatchers.Unconfined
        )
    }
}





