package com.f776.ecopan.data.reminder.di

import com.f776.ecopan.data.reminder.ReminderSchedulerImpl
import com.f776.ecopan.data.reminder.android.boot.BootScheduler
import com.f776.ecopan.data.reminder.android.boot.BootSchedulerWorker
import com.f776.ecopan.data.reminder.android.notification.AppNotificationChannel
import com.f776.ecopan.data.reminder.android.notification.ReminderNotificationPoster
import com.f776.ecopan.data.reminder.notification.ReminderNotifier
import com.f776.ecopan.data.reminder.permission.ReminderPermissionManagerImpl
import com.f776.ecopan.data.reminder.utils.Toaster
import com.f776.ecopan.domain.reminder.ReminderScheduler
import com.f776.ecopan.domain.reminder.permission.ReminderPermissionManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.MainCoroutineDispatcher
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

internal actual val PlatformModule: Module = module {
    factoryOf(::Toaster)
    factoryOf(::AppNotificationChannel)
    factoryOf(::ReminderNotificationPoster)
    factoryOf(::ReminderSchedulerImpl).bind<ReminderScheduler>()

    factory {
        ReminderNotifier(
            poster = get(),
            ioDispatcher = get<CoroutineDispatcher>(named("ioDispatcher"))
        )
    }

    factoryOf(::BootScheduler)

    workerOf(::BootSchedulerWorker)

    factory {
        ReminderPermissionManagerImpl(
            context = get(),
            permissionsController = get(),
            toaster = get(),
            ioDispatcher = get<CoroutineDispatcher>(named("ioDispatcher")),
            mainDispatcher = get<MainCoroutineDispatcher>(named("mainDispatcher"))
        )
    }.bind<ReminderPermissionManager>()
}
