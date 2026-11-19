package com.f776.ecopan.feature.pos.screens.pos

internal sealed interface PosEvent {
    /** The Screen resolves the localized text via `stringResource` at collection time. */
    data object NoActiveBranchOrSession : PosEvent

    /** Barcode scanned (via the [com.f776.ecopan.feature.pos.screens.pos.components.BarcodeCapture]
     *  HID sink) matched no active product (05 §3 item 4, §6 edge cases). */
    data object UnknownBarcodeScanned : PosEvent
}
