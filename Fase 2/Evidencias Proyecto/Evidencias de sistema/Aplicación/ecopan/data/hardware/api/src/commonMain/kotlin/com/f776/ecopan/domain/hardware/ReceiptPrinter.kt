package com.f776.ecopan.domain.hardware

import kotlinx.coroutines.flow.StateFlow

interface ReceiptPrinter {
    val status: StateFlow<DeviceStatus>
    suspend fun print(doc: ReceiptDoc): PrintResult

    /** The drawer usually hangs off the printer (RJ11) — see `PrinterKickDrawer` (04 §2). */
    suspend fun kickDrawer()
}
