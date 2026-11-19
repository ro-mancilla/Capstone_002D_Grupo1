package com.f776.ecopan.data.reminder.di

import com.f776.ecopan.domain.reminder.RemindersProvider
import org.koin.core.module.Module
import org.koin.dsl.module

val ReminderDataModule = module {
    includes(PlatformModule)

    // Default no-op: bind your app's real RemindersProvider over this to make boot-reschedule
    // (BootScheduler/BootSchedulerWorker) re-arm your actual reminders after a reboot.
    single<RemindersProvider> { RemindersProvider { emptyList() } }
}

internal expect val PlatformModule: Module
