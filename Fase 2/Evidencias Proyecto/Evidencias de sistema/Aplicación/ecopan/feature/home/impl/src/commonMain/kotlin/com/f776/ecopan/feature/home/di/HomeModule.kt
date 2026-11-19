package com.f776.ecopan.feature.home.di

import com.f776.ecopan.data.example.di.ExampleDataModule
import com.f776.ecopan.data.reminder.di.ReminderDataModule
import com.f776.ecopan.feature.home.navigation.HomeNavigationModule
import com.f776.ecopan.feature.home.screens.home.HomeViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val HomeModule = module {
    includes(HomeNavigationModule, ReminderDataModule, ExampleDataModule)
    viewModel {
        HomeViewModel(
            noteRepository = get(),
            reminderScheduler = get(),
            reminderPermissionManager = get(),
        )
    }
}
