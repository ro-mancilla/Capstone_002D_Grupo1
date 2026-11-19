package com.f776.ecopan.data.hardware

import com.f776.ecopan.domain.hardware.DeviceStatus
import com.f776.ecopan.domain.hardware.PaperWidth
import com.f776.ecopan.domain.hardware.PrintResult
import com.f776.ecopan.domain.hardware.ReceiptDoc
import com.f776.ecopan.domain.hardware.ReceiptPreviewSource
import com.f776.ecopan.domain.hardware.ReceiptPrinter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The demo printer (04 §2) -- SIMULATED, no printer is driven. It lays [ReceiptDoc] out with the same
 * [EscPosReceipt] builder the real `EscPosPrinter` sends to paper (width from `paperWidthMm`, 58 or
 * 80) and exposes the result as text via [renderedReceipts] / [lastPreview] for the POS preview.
 * Always CONNECTED.
 */
internal class MockPrinter(config: Map<String, String>) : ReceiptPrinter, ReceiptPreviewSource {

    private val paper = PaperWidth.fromConfig(config)

    override var lastPreview: String? = null
        private set


    private val _status = MutableStateFlow(DeviceStatus.CONNECTED)
    override val status = _status.asStateFlow()

    // replay = 1 so a UI that starts collecting after a print/kick already happened still sees
    // the most recent one, rather than hanging waiting for the next.
    private val _renderedReceipts = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 16)
    val renderedReceipts: Flow<String> = _renderedReceipts

    private val _drawerKicks = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 16)
    val drawerKicks: Flow<Unit> = _drawerKicks

    override suspend fun print(doc: ReceiptDoc): PrintResult {
        val preview = EscPosReceipt.build(doc, paper).preview
        lastPreview = preview
        _renderedReceipts.emit(preview)
        return PrintResult.Ok
    }

    override suspend fun kickDrawer() {
        _drawerKicks.emit(Unit)
    }
}
