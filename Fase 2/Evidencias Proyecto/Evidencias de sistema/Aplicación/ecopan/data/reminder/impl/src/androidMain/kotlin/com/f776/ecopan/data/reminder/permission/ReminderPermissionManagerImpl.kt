package com.f776.ecopan.data.reminder.permission

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.f776.ecopan.data.reminder.utils.Toaster
import com.f776.ecopan.domain.reminder.permission.PermissionResult
import com.f776.ecopan.domain.reminder.permission.ReminderPermissionManager
import dev.icerock.moko.permissions.DeniedAlwaysException
import dev.icerock.moko.permissions.DeniedException
import dev.icerock.moko.permissions.Permission
import dev.icerock.moko.permissions.PermissionsController
import dev.icerock.moko.permissions.RequestCanceledException
import dev.icerock.moko.permissions.notifications.REMOTE_NOTIFICATION
import dev.shreyaspatil.permissionFlow.PermissionFlow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import ecopan.data.reminder.impl.generated.resources.Res
import ecopan.data.reminder.impl.generated.resources.permission_denied_message
import org.jetbrains.compose.resources.getString

internal actual class ReminderPermissionManagerImpl(
    private val context: Context,
    private val permissionsController: PermissionsController,
    private val toaster: Toaster,
    private val ioDispatcher: CoroutineDispatcher,
    private val mainDispatcher: CoroutineDispatcher
) : ReminderPermissionManager {
    private val permissionFlow = PermissionFlow.getInstance()

    actual override fun isNotificationPermissionRequired(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    actual override fun hasAlarmPermission(): Flow<Boolean> {
        return when {
            // Android 13+: USE_EXACT_ALARM is auto-granted if declared.
            // canScheduleExactAlarms() will return true.
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                flowOf(true)
            }
            // Android 12 & 12L: Check if SCHEDULE_EXACT_ALARM is granted or revoked.
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                flowOf(alarmManager.canScheduleExactAlarms())
            }
            // Android 11 and below: Always allowed.
            else -> flowOf(true)
        }
    }

    actual override fun hasNotificationPermission(): Flow<Boolean> {
        // Check notification permission (Android 13+)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionFlow.getPermissionState(Manifest.permission.POST_NOTIFICATIONS)
                .map { it.isGranted }
        } else {
            flowOf(true)
        }
    }

    actual override fun requestAlarmPermission() {
        // Only Android 12 (API 31 & 32) needs a manual request/redirect.
        // On Android 13+, USE_EXACT_ALARM makes this unnecessary.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
        }
    }

    actual override suspend fun requestNotificationPermission(): PermissionResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return PermissionResult.GRANTED
        }

        return try {
            permissionsController.providePermission(Permission.REMOTE_NOTIFICATION)
            PermissionResult.GRANTED
        } catch (_: DeniedAlwaysException) {
            toast()
            permissionsController.openAppSettings()
            PermissionResult.DENIED_ALWAYS
        } catch (_: DeniedException) {
            // Permission was denied.
            toast()
            PermissionResult.DENIED
        } catch (_: RequestCanceledException) {
            toast()
            PermissionResult.CANCELLED
        }
    }

    private suspend fun loadToastMessage() = withContext(ioDispatcher) {
        getString(Res.string.permission_denied_message)
    }

    private suspend fun toast() = withContext(mainDispatcher) {
        toaster.showToast(loadToastMessage())
    }
}