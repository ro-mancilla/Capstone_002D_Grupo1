package com.f776.ecopan.domain.hardware

/**
 * Optional capability of a simulated [ReceiptPrinter] (discovered with `printer as? ReceiptPreviewSource`,
 * like [ManualWeightEntry]): the thermal-receipt text for the most recent [ReceiptPrinter.print], laid
 * out exactly as the real ESC/POS driver would send it. It is a preview from a simulated driver, not
 * evidence that any physical printer was driven.
 */
interface ReceiptPreviewSource {
    /** `null` until something has been printed. */
    val lastPreview: String?
}

/**
 * Marker for a [CashDrawer] with no hardware behind it: `open()` only records an event. The till
 * uses it to say "cajón abierto (simulado)" instead of leaving the cashier to look for a drawer.
 */
interface SimulatedDrawer

/**
 * Optional capability of a [Scale] that can play a scripted weight sequence for a chosen product
 * (discovered with `scale as? ProductWeightScript`). Drivers with no script for [productName] keep
 * their ordinary behaviour.
 */
interface ProductWeightScript {
    fun onProductSelected(productName: String)
}
