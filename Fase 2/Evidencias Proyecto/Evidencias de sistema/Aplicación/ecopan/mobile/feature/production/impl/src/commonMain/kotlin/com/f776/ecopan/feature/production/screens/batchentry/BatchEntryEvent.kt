package com.f776.ecopan.feature.production.screens.batchentry

internal sealed interface BatchEntryEvent {
    data class BatchLogged(val productName: String, val kgLabel: String) : BatchEntryEvent
    data class BatchLoggedNoDecrement(val productName: String, val kgLabel: String) : BatchEntryEvent
}
