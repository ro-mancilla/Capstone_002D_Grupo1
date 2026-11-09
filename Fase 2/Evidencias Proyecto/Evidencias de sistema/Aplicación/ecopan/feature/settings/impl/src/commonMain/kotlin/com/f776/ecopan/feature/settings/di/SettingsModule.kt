package com.f776.ecopan.feature.settings.di

import com.f776.ecopan.data.hardware.di.HardwareDataModule
import com.f776.ecopan.data.preferences.di.PreferencesModule
import com.f776.ecopan.data.reminder.di.ReminderDataModule
import com.f776.ecopan.data.sync.di.SyncDataModule
import com.f776.ecopan.data.tenant.di.TenantDataModule
import com.f776.ecopan.feature.settings.navigation.SettingsNavigationModule
import com.f776.ecopan.feature.settings.screens.settings.SettingsViewModel
import com.f776.ecopan.feature.settings.screens.settings.devices.DevicesViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val SettingsModule = module {
    includes(SettingsNavigationModule, ReminderDataModule, PreferencesModule, HardwareDataModule, TenantDataModule, SyncDataModule)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::DevicesViewModel)
}
