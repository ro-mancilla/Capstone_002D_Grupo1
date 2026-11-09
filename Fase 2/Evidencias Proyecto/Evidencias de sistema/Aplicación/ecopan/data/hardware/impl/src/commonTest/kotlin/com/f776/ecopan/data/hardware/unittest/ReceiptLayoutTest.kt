package com.f776.ecopan.data.hardware.unittest

import com.f776.ecopan.core.domain.catalog.PaymentMethod
import com.f776.ecopan.domain.hardware.PaperWidth
import com.f776.ecopan.domain.hardware.ReceiptDoc
import com.f776.ecopan.domain.hardware.ReceiptLayout
import com.f776.ecopan.domain.hardware.ReceiptLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** Golden tests for the receipt text: the same lines go to the preview and to the printer. */
internal class ReceiptLayoutTest {

    private val saleId = Uuid.random()

    private fun cashSale(number: Int = 1) = ReceiptDoc(
        header = listOf("Panadería El Sol", "Sucursal Principal"),
        lines = listOf(
            ReceiptLine("Marraqueta", "0,842 kg", 3200, 2694, perKg = true),
            ReceiptLine("Empanada de Pino", "2", 1800, 3600),
        ),
        totalMinor = 6294,
        paymentMethod = PaymentMethod.CASH,
        footer = listOf("Gracias por su compra"),
        saleId = saleId,
        receiptNumber = number,
        issuedAtLabel = "07-10-2026 08:42",
        cashTenderedMinor = 10_000,
        changeMinor = 3706,
    )

    @Test
    fun `58 mm receipt is the 32 column golden`() {
        val expected = """
            |        Panadería El Sol
            |       Sucursal Principal
            |--------------------------------
            |07-10-2026 08:42
            |--------------------------------
            |Marraqueta
            |  0,842 kg @ ${'$'}3.200/kg    ${'$'}2.694
            |Empanada de Pino
            |  2 @ ${'$'}1.800              ${'$'}3.600
            |--------------------------------
            |Total                     ${'$'}6.294
            |Efectivo                 ${'$'}10.000
            |Vuelto                    ${'$'}3.706
            |--------------------------------
            |Venta N.º 0001
            |     Gracias por su compra
        """.trimMargin().lines()

        assertEquals(expected, ReceiptLayout.render(cashSale(), PaperWidth.Mm58))
    }

    @Test
    fun `80 mm receipt is the 48 column golden`() {
        val rule = "-".repeat(48)
        val expected = listOf(
            " ".repeat(16) + "Panadería El Sol",
            " ".repeat(15) + "Sucursal Principal",
            rule,
            "07-10-2026 08:42",
            rule,
            "Marraqueta",
            "  0,842 kg @ \$3.200/kg" + " ".repeat(48 - 22 - 6) + "\$2.694",
            "Empanada de Pino",
            "  2 @ \$1.800" + " ".repeat(48 - 12 - 6) + "\$3.600",
            rule,
            "Total" + " ".repeat(48 - 5 - 6) + "\$6.294",
            "Efectivo" + " ".repeat(48 - 8 - 7) + "\$10.000",
            "Vuelto" + " ".repeat(48 - 6 - 6) + "\$3.706",
            rule,
            "Venta N.º 0001",
            " ".repeat(13) + "Gracias por su compra",
        )

        assertEquals(expected, ReceiptLayout.render(cashSale(), PaperWidth.Mm80))
    }

    @Test
    fun `no line is ever wider than the paper`() {
        val long = cashSale().copy(
            lines = listOf(ReceiptLine("Pan de masa madre con semillas y nueces de la casa", "12,345 kg", 12_990, 160_361, perKg = true)),
            header = listOf("Panadería y Pastelería El Sol de la Cordillera Ltda"),
        )
        for (width in PaperWidth.entries) {
            val lines = ReceiptLayout.render(long, width)
            assertTrue(lines.all { it.length <= width.columns }, "a line overflowed ${width.columns} columns: ${lines.maxBy { it.length }}")
        }
    }

    @Test
    fun `the correlativo is padded to four digits and is never the sale uuid`() {
        val lines = ReceiptLayout.render(cashSale(number = 42), PaperWidth.Mm58)
        assertTrue("Venta N.º 0042" in lines)
        assertFalse(lines.any { saleId.toString() in it }, "the UUID must never be printed")
        assertEquals("Venta N.º 12345", ReceiptLayout.receiptNumberLabel(12_345))
    }

    @Test
    fun `a card sale prints the method against the total and no change`() {
        val card = cashSale().copy(paymentMethod = PaymentMethod.CARD, cashTenderedMinor = null, changeMinor = null)
        val lines = ReceiptLayout.render(card, PaperWidth.Mm58)
        assertTrue(lines.any { it.startsWith("Tarjeta") && it.endsWith("\$6.294") })
        assertFalse(lines.any { it.startsWith("Vuelto") })
    }

    @Test
    fun `money is dot grouped with no decimals`() {
        assertEquals("\$0", ReceiptLayout.formatMoney(0))
        assertEquals("\$999", ReceiptLayout.formatMoney(999))
        assertEquals("\$1.234.567", ReceiptLayout.formatMoney(1_234_567))
        assertEquals("-\$3.706", ReceiptLayout.formatMoney(-3706))
    }

    @Test
    fun `paper width is read from the printer config and defaults to 58 mm`() {
        assertEquals(PaperWidth.Mm80, PaperWidth.fromConfig(mapOf("paperWidthMm" to "80")))
        assertEquals(PaperWidth.Mm58, PaperWidth.fromConfig(emptyMap()))
        assertEquals(PaperWidth.Mm58, PaperWidth.fromConfig(mapOf("paperWidthMm" to "100")))
        assertEquals(32, PaperWidth.Mm58.columns)
        assertEquals(48, PaperWidth.Mm80.columns)
    }
}
