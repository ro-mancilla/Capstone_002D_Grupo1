package com.f776.ecopan.feature.production.screens.batchentry

import kotlin.uuid.Uuid

internal sealed interface BatchEntryAction {
    data class OnSelectProduct(val productId: Uuid) : BatchEntryAction
    data class OnKeypadDigit(val digit: Char) : BatchEntryAction
    data object OnKeypadBackspace : BatchEntryAction
    data object OnConfirmRequested : BatchEntryAction
    data object OnConfirm : BatchEntryAction

    /** Dismisses the inline "lote registrado" confirmation. */
    data object OnDismissLogged : BatchEntryAction

    /** Sync status indicator (03 §6.1) -- tap triggers a manual sync attempt, same as POS. */
    data object OnSyncIndicatorTapped : BatchEntryAction

    /** "Ver los N productos" / "Ver menos" on the suggested-production card. */
    data object OnToggleSuggestedExpanded : BatchEntryAction

    /** The screen came back to the foreground: re-evaluate "tomorrow" and which suggestions are stale. */
    data object OnResumed : BatchEntryAction
}
