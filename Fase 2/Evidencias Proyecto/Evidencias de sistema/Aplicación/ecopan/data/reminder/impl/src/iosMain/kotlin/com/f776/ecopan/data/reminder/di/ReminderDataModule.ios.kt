package com.f776.ecopan.data.reminder.di

import com.f776.ecopan.data.reminder.ReminderSchedulerImpl
import com.f776.ecopan.data.reminder.permission.ReminderPermissionManagerImpl
import com.f776.ecopan.data.reminder.utils.Toaster
import com.f776.ecopan.domain.reminder.ReminderScheduler
import com.f776.ecopan.domain.reminder.permission.ReminderPermissionManager
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.bind
import org.koin.dsl.module

internal actual val PlatformModule: Module = module {
    factoryOf(::Toaster)
    factoryOf(::ReminderSchedulerImpl).bind<ReminderScheduler>()
    factoryOf(::ReminderPermissionManagerImpl).bind<ReminderPermissionManager>()
}
