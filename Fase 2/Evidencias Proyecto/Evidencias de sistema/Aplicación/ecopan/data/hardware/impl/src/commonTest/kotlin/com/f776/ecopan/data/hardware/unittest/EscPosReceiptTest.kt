package com.f776.ecopan.data.hardware.unittest

import com.f776.ecopan.core.domain.catalog.PaymentMethod
import com.f776.ecopan.data.hardware.EscPosReceipt
import com.f776.ecopan.domain.hardware.PaperWidth
import com.f776.ecopan.domain.hardware.ReceiptDoc
import com.f776.ecopan.domain.hardware.ReceiptLayout
import com.f776.ecopan.domain.hardware.ReceiptLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

internal class EscPosReceiptTest {

    private val saleId = Uuid.parse("12345678-1234-1234-1234-123456789abc")

    private fun doc(
        lines: List<ReceiptLine> = listOf(
            ReceiptLine("Marraqueta", "0,842 kg", 2500, 2105),
            ReceiptLine("Empanada de Pino", "x2", 1200, 2400),
        ),
        total: Long = 4505,
        header: List<String> = listOf("Panadería El Sol", "Sucursal Principal"),
    ) = ReceiptDoc(
        header = header,
        lines = lines,
        totalMinor = total,
        paymentMethod = PaymentMethod.CASH,
        footer = listOf("Gracias por su compra"),
        saleId = saleId,
    )

    private fun ByteArray.ints() = map { it.toInt() and 0xFF }

    @Test
    fun `bytes open with ESC at then ESC t 19 and close with a feed and a partial cut`() {
        val bytes = EscPosReceipt.build(doc(), PaperWidth.Mm58).bytes.ints()

        assertEquals(listOf(0x1B, 0x40, 0x1B, 0x74, 19), bytes.take(5))
        assertEquals(listOf(0x1B, 0x64, 4, 0x1D, 0x56, 0x01), bytes.takeLast(6))
    }

    @Test
    fun `golden bytes for a tiny receipt pin the line feeds and code page 858 values`() {
        val tiny = ReceiptDoc(
            header = listOf("áéíóúñÑ¿¡ü"),
            lines = emptyList(),
            totalMinor = 0,
            paymentMethod = PaymentMethod.CASH,
            footer = emptyList(),
            saleId = saleId,
        )

        val bytes = EscPosReceipt.build(tiny, PaperWidth.Mm58).bytes.ints()

        // The header line is centred, so skip its padding: a0 82 a1 a2 a3 a4 a5 a8 ad 81 (CP858)
        // followed by LF, right after ESC @ ESC t 19.
        assertEquals(
            listOf(0xA0, 0x82, 0xA1, 0xA2, 0xA3, 0xA4, 0xA5, 0xA8, 0xAD, 0x81, 0x0A),
            bytes.drop(5).dropWhile { it == 0x20 }.take(11),
        )
    }

    @Test
    fun `capital accents and the euro sign use their CP858 codes`() {
        val bytes = EscPosReceipt.build(doc(header = listOf("ÁÉÍÓÚÜ€")), PaperWidth.Mm58).bytes.ints()

        assertEquals(listOf(0xB5, 0x90, 0xD6, 0xE0, 0xE9, 0x9A, 0xD5, 0x0A), bytes.drop(5).dropWhile { it == 0x20 }.take(8))
    }

    @Test
    fun `a character the printer cannot print is a question mark on paper and in the preview`() {
        val receipt = EscPosReceipt.build(doc(header = listOf("Café 日本")), PaperWidth.Mm58)

        assertEquals("Café ??", receipt.preview.lines().first().trim())
        assertEquals('?'.code, receipt.bytes.ints().drop(5).dropWhile { it == 0x20 }[5])
    }

    @Test
    fun `58 mm lays out 32 columns and 80 mm lays out 48`() {
        val narrow = EscPosReceipt.build(doc(), PaperWidth.Mm58).preview.lines()
        val wide = EscPosReceipt.build(doc(), PaperWidth.Mm80).preview.lines()

        assertEquals(32, narrow.maxOf { it.length })
        assertEquals(48, wide.maxOf { it.length })
        assertTrue(wide.first { it.startsWith("Total") }.let { it.length == 48 && it.endsWith("$4.505") })
    }

    @Test
    fun `totals are right aligned and the method is the Spanish label`() {
        val preview = EscPosReceipt.build(doc(), PaperWidth.Mm58).preview

        val total = preview.lines().first { it.startsWith("Total") }
        assertEquals(32, total.length)
        assertTrue(total.endsWith("$4.505"))
        assertTrue(preview.contains("Efectivo"))
    }

    @Test
    fun `a long left side is elided so the price is never cut`() {
        val line = ReceiptLine("Pan", "0,842 kg grande de masa madre", 2500, 2105)

        val detail = EscPosReceipt.build(doc(lines = listOf(line)), PaperWidth.Mm58).preview.lines()
            .first { it.endsWith("$2.105") }

        assertEquals(32, detail.length)
        assertTrue(detail.contains(".."), detail)
        assertTrue(detail.endsWith(" $2.105"))
    }

    @Test
    fun `a million-peso total keeps every digit`() {
        val line = ReceiptLine("Torta", "x1", 1_234_567, 1_234_567)

        val preview = EscPosReceipt.build(doc(lines = listOf(line), total = 1_234_567), PaperWidth.Mm58).preview

        assertTrue(preview.lines().any { it.startsWith("Total") && it.endsWith("$1.234.567") && it.length == 32 })
        assertTrue(preview.lines().any { it.endsWith("$1.234.567") && it.startsWith("  x1 @") })
    }

    @Test
    fun `a price too wide to sit beside any text goes on its own line whole`() {
        val lines = ReceiptLayout.twoColumns("  x1 @ $1", "$1.234.567", 10)

        assertEquals(listOf("  x1 @ $1", "$1.234.567"), lines)
    }

    @Test
    fun `a price with room for one character keeps it and shortens the left`() {
        assertEquals(listOf("a $1.234.56"), ReceiptLayout.twoColumns("abcdef", "$1.234.56", 11))
    }

    @Test
    fun `negative money keeps its sign in front of the dollar`() {
        val line = ReceiptLine("Descuento", "x1", -500, -500)

        val preview = EscPosReceipt.build(doc(lines = listOf(line), total = -500), PaperWidth.Mm58).preview

        assertTrue(preview.lines().any { it.startsWith("Total") && it.endsWith("-$500") })
        assertEquals("-$1.234", ReceiptLayout.formatMoney(-1234))
        assertEquals("$0", ReceiptLayout.formatMoney(0))
    }

    @Test
    fun `a long product name wraps onto the next line instead of being cut`() {
        val name = "Pan de molde integral con semillas de girasol y linaza"
        val preview = EscPosReceipt.build(doc(lines = listOf(ReceiptLine(name, "x1", 100, 100))), PaperWidth.Mm58).preview

        assertTrue(preview.lines().all { it.length <= 32 })
        assertTrue(preview.replace("\n", "").contains(name))
    }

    @Test
    fun `the whole sale id is on the paper`() {
        val preview = EscPosReceipt.build(doc(), PaperWidth.Mm58).preview

        assertTrue(preview.lines().all { it.length <= 32 })
        assertEquals(saleId.toString(), preview.substringAfter("Venta:\n").lines().joinToString(""))
    }
}
