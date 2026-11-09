package com.f776.ecopan.di

import com.f776.ecopan.AppViewModel
import com.f776.ecopan.core.navigation.Navigator
import com.f776.ecopan.data.hardware.di.HardwareDataModule
import com.f776.ecopan.data.preferences.di.PreferencesModule
import com.f776.ecopan.data.reminder.di.ReminderDataModule
import com.f776.ecopan.data.sync.di.SyncDataModule
import com.f776.ecopan.feature.auth.di.AuthModule
import com.f776.ecopan.feature.settings.di.SettingsModule
import com.f776.ecopan.feature.settings.navigation.SettingsNavigationModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import com.f776.locale.di.LocaleModule
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Everything every client shares. Anything a *single* client owns goes in that platform's
 * `PlatformModule` instead: `PosModule` on the till (`jvmMain`), `MobileModule` on the baker's
 * phone. Those feature modules are not merely unused on the other client — they are absent from
 * its compile classpath, because `:jvm:feature:*` has no mobile target and `:mobile:feature:*` has
 * no JVM one. See `navigation/TopLevelDestinations.kt`.
 */
val AppModule = module {
    includes(
        PlatformModule,
        LocaleModule,
        AuthModule,
        PreferencesModule,
        ReminderDataModule,
        SyncDataModule,
        HardwareDataModule,
        SettingsModule,
        SettingsNavigationModule,
    )
    viewModelOf(::AppViewModel)
    single(named("ioDispatcher")) { Dispatchers.IO }
    single(named("defaultDispatcher")) { Dispatchers.Default }
    single(named("mainDispatcher")) { Dispatchers.Main }
    factory(named("ioCoroutineScope")) { CoroutineScope(Dispatchers.IO + SupervisorJob()) }
    factory(named("defaultCoroutineScope")) { CoroutineScope(Dispatchers.Default + SupervisorJob()) }
    single { Navigator() }
}

expect val PlatformModule: Module
