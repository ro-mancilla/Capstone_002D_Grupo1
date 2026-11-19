package com.f776.locale.di

import com.f776.locale.AndroidClockFormat
import com.f776.locale.ClockFormat
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val PlatformLocaleModule: Module = module {
    single<ClockFormat> { AndroidClockFormat(context = get()) }
}
