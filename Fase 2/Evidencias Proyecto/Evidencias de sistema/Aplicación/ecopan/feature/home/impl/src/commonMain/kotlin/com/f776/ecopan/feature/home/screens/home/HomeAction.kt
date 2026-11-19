package com.f776.ecopan.feature.home.screens.home

import kotlin.time.Instant
import kotlin.uuid.Uuid

internal sealed interface HomeAction {
    data class OnAddNote(val title: String, val body: String) : HomeAction
    data class OnDeleteNote(val id: Uuid) : HomeAction
    data class OnRemindNote(val id: Uuid, val triggerAt: Instant) : HomeAction
}
