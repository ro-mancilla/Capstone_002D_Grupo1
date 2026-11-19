package com.f776.locale.di

import com.f776.locale.ClockFormat
import com.f776.locale.LocaleClockFormat
import org.koin.core.module.Module
import org.koin.dsl.module

/** iOS folds the user's "24-Hour Time" setting into the current locale, so no Context is needed. */
internal actual val PlatformLocaleModule: Module = module {
    single<ClockFormat> { LocaleClockFormat() }
}
