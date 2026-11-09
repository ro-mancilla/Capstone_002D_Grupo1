package com.f776.ecopan.data.hardware.unittest

import com.f776.ecopan.core.domain.catalog.PaymentMethod
import com.f776.ecopan.data.hardware.EscPosReceipt
import com.f776.ecopan.data.hardware.MockPrinter
import com.f776.ecopan.domain.hardware.DeviceStatus
import com.f776.ecopan.domain.hardware.PaperWidth
import com.f776.ecopan.domain.hardware.PrintResult
import com.f776.ecopan.domain.hardware.ReceiptDoc
import com.f776.ecopan.domain.hardware.ReceiptLine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

internal class MockPrinterTest {

    private val printer = MockPrinter(emptyMap())

    @Test
    fun `is always CONNECTED`() {
        assertEquals(DeviceStatus.CONNECTED, printer.status.value)
    }

    @Test
    fun `print always succeeds and renders the receipt for the on-screen panel`() = runTest {
        val doc = ReceiptDoc(
            header = listOf("Panadería El Sol", "Sucursal Centro"),
            lines = listOf(ReceiptLine("Marraqueta", "0,842 kg", 1200, 1010, perKg = true)),
            totalMinor = 1010,
            paymentMethod = PaymentMethod.CASH,
            footer = listOf("Gracias por su compra"),
            saleId = Uuid.random(),
        )

        val result = printer.print(doc)

        assertEquals(PrintResult.Ok, result)
        val rendered = printer.renderedReceipts.first()
        assertTrue(rendered.contains("Marraqueta"))
        assertTrue(rendered.contains("$1.010"))
        // The paper says the Spanish word, never the enum name.
        assertTrue(rendered.contains("Efectivo"))
        assertTrue(!rendered.contains("CASH"))
    }

    @Test
    fun `the preview is the receipt builder's preview for the configured paper`() = runTest {
        val doc = ReceiptDoc(
            header = listOf("Panadería El Sol"),
            lines = listOf(ReceiptLine("Marraqueta", "0,842 kg", 2500, 2105)),
            totalMinor = 2105,
            paymentMethod = PaymentMethod.CASH,
            footer = emptyList(),
            saleId = Uuid.random(),
        )

        printer.print(doc)

        assertEquals(EscPosReceipt.build(doc, PaperWidth.Mm58).preview, printer.lastPreview)
    }

    @Test
    fun `paper width comes from config`() = runTest {
        val wide = MockPrinter(mapOf("paperWidthMm" to "80"))
        val doc = ReceiptDoc(listOf("x"), emptyList(), 100, PaymentMethod.CARD, emptyList(), Uuid.random())

        wide.print(doc)

        assertEquals(48, wide.lastPreview!!.lines().maxOf { it.length })
    }

    @Test
    fun `lastPreview is null before anything is printed`() {
        assertEquals(null, printer.lastPreview)
    }

    @Test
    fun `lays the receipt out at the configured paper width`() = runTest {
        val doc = ReceiptDoc(
            header = listOf("Panadería El Sol"),
            lines = listOf(ReceiptLine("Marraqueta", "1", 1200, 1200)),
            totalMinor = 1200,
            paymentMethod = PaymentMethod.CARD,
            footer = emptyList(),
            saleId = Uuid.random(),
            receiptNumber = 7,
        )

        val wide = MockPrinter(mapOf("paperWidthMm" to "80")).also { it.print(doc) }.renderedReceipts.first()
        val narrow = MockPrinter(emptyMap()).also { it.print(doc) }.renderedReceipts.first()

        assertEquals(48, wide.lines().maxOf { it.length })
        assertEquals(32, narrow.lines().maxOf { it.length })
        assertTrue(narrow.contains("Venta N.º 0007"))
    }

    @Test
    fun `kickDrawer emits a drawer event`() = runTest {
        printer.kickDrawer()

        printer.drawerKicks.first()
    }
}
