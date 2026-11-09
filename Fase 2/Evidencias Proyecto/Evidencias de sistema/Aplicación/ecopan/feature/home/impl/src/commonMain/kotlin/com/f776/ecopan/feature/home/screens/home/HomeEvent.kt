package com.f776.ecopan.feature.home.screens.home

internal sealed interface HomeEvent {
    data class OnReminderScheduled(val noteTitle: String) : HomeEvent
    data object OnPermissionDenied : HomeEvent
}
