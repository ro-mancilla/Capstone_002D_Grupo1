package com.f776.ecopan.feature.pos.screens.pos

import com.f776.ecopan.core.domain.catalog.PaymentMethod
import kotlin.uuid.Uuid

internal sealed interface PosAction {
    data class OnCategorySelected(val category: String) : PosAction
    data class OnSearchQueryChanged(val query: String) : PosAction
    data class OnProductTapped(val productId: Uuid) : PosAction
    data class OnManualWeightChanged(val kgText: String) : PosAction
    /** [grams] is the reading on the button when it was pressed, so a later reading cannot change what is sold. */
    data class OnAddWeighedLine(val grams: Long) : PosAction
    data object OnCloseWeighPanel : PosAction
    data class OnVoidLine(val lineId: Uuid) : PosAction
    /** Steps a UNIT line's quantity by [delta]; stepping the last unit off removes the line. */
    data class OnStepLineQuantity(val lineId: Uuid, val delta: Int) : PosAction
    data object OnDiscardSale : PosAction
    data object OnCharge : PosAction
    data object OnSwitchToManualScale : PosAction

    // Barcode HID capture (05 §3 item 4, §9 item 8)
    data class OnBarcodeScanned(val code: String) : PosAction

    // Batch-ready notice (05 §3 item 9, §9 item 10)
    data class OnDismissBatchNotice(val id: Uuid) : PosAction

    // Sync status indicator (03 §6.1) -- tap triggers a manual sync attempt.
    data object OnSyncIndicatorTapped : PosAction

    /** Re-login for a device session that has died mid-shift -- see `ReconnectUi`. */
    data class OnReconnectUsernameChanged(val value: String) : PosAction
    data class OnReconnectPasswordChanged(val value: String) : PosAction
    data object OnReconnectSubmitted : PosAction
    data object OnReconnectDismissed : PosAction

    // Payment dialog
    data class OnPaymentMethodSelected(val method: PaymentMethod) : PosAction
    data object OnBackToMethodPicker : PosAction
    data class OnCashTenderedChanged(val text: String) : PosAction
    data class OnQuickCashChip(val amountMinor: Long) : PosAction
    data object OnConfirmCashPayment : PosAction
    data object OnStartCardAuth : PosAction
    data object OnDeclineCardAuthDemo : PosAction
    data object OnConfirmOtherPayment : PosAction
    data object OnCancelPayment : PosAction
    data object OnDismissReceipt : PosAction

    /** Prints the shown boleta again after a failed print; the sale is already recorded. */
    data object OnReprintReceipt : PosAction

    // Till (read-only today's sales)
    data object OnOpenTill : PosAction
    data object OnCloseTill : PosAction
}
