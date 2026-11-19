package com.f776.ecopan.data.reminder.android.notification

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.f776.ecopan.data.reminder.impl.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class AppNotificationChannelAndroidHostTest {

    @Test
    @Config(sdk = [Build.VERSION_CODES.O])
    fun createNotificationChannel_registersExpectedChannel() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        AppNotificationChannel(context).createNotificationChannel()

        val channel = notificationManager.getNotificationChannel(AppNotificationChannel.CHANNEL_ID)
        assertNotNull(channel)
        assertEquals(context.getString(R.string.channel_name), channel.name)
        assertEquals(context.getString(R.string.channel_description), channel.description)
    }
}

