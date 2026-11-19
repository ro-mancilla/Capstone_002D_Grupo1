package com.f776.locale.di

import com.f776.locale.ClockFormat
import com.f776.locale.LocaleClockFormat
import org.koin.core.module.Module
import org.koin.dsl.module

/** Desktop has no system-wide clock override, so the locale convention is the whole answer. */
internal actual val PlatformLocaleModule: Module = module {
    single<ClockFormat> { LocaleClockFormat() }
}
