package com.f776.ecopan.notifier.di

import com.f776.ecopan.data.core.BaseSettings
import com.f776.ecopan.data.core.DataModule
import com.f776.ecopan.notifier.core.Settings
import com.f776.ecopan.notifier.domain.NotificationProcessor
import com.f776.ecopan.notifier.domain.NotificationProcessorImpl
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val NotifierModule = module {
    includes(DataModule)

    singleOf(::Settings).bind<BaseSettings>()
    singleOf(::NotificationProcessorImpl).bind<NotificationProcessor>()
}
