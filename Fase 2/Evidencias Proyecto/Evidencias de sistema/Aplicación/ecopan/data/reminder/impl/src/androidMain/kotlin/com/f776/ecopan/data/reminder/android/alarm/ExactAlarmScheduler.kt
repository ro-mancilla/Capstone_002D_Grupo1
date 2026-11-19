package com.f776.ecopan.data.reminder.android.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.AlarmManagerCompat

/**
 * The AlarmManager mechanics shared by every reminder kind.
 *
 * Class reminders and deadline reminders differ only in which receiver they wake, how their
 * request code is derived and what they put in the intent. Everything below that — the
 * PendingIntent flags, the exact-alarm permission check and the cancel path — is identical, and
 * was previously copied per scheduler with the copies drifting apart.
 *
 * Alarms are addressed by ([receiver], [requestCode]). Callers own the request-code scheme;
 * distinct receiver classes already make collisions across kinds impossible, because a
 * PendingIntent's identity includes the component it targets.
 */
internal class ExactAlarmScheduler(private val context: Context) {

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Arms an exact alarm, returning whether it was actually scheduled.
     *
     * Returns false rather than throwing when the OS refuses: on API 31–32 the user can revoke
     * the exact-alarm permission at any time, and a `SecurityException` escaping here would
     * surface as a crash while saving.
     */
    fun schedule(
        receiver: Class<*>,
        requestCode: Int,
        triggerAtMillis: Long,
        extras: Intent.() -> Unit = {}
    ): Boolean {
        if (!canScheduleExactAlarms()) {
            Log.w(TAG, "Exact alarms not permitted; skipping alarm $requestCode")
            return false
        }

        val intent = Intent(context, receiver).apply(extras)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return try {
            AlarmManagerCompat.setExactAndAllowWhileIdle(
                alarmManager,
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
            true
        } catch (e: SecurityException) {
            // Revoked between the check above and this call.
            Log.w(TAG, "Denied exact alarm $requestCode", e)
            false
        }
    }

    /**
     * Cancels the alarm at ([receiver], [requestCode]) if one is pending.
     *
     * Uses [PendingIntent.FLAG_NO_CREATE] so that cancelling something that was never scheduled
     * is a no-op. Retrieving with `FLAG_UPDATE_CURRENT` here would instead overwrite the live
     * PendingIntent's extras with the empty ones of the intent built for the cancel, so an alarm
     * firing in the window before the cancel landed would reach its receiver stripped of payload.
     */
    fun cancel(receiver: Class<*>, requestCode: Int) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, receiver),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return

        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    fun canScheduleExactAlarms(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

    private companion object {
        const val TAG = "ExactAlarmScheduler"
    }
}
