package com.f776.ecopan.data.hardware

import com.f776.ecopan.domain.hardware.PaperWidth
import com.f776.ecopan.domain.hardware.ReceiptDoc
import com.f776.ecopan.domain.hardware.ReceiptLayout

/**
 * A receipt as the printer is sent it: the raw ESC/POS [bytes] and the [preview] -- the lines a
 * person would read off the paper.
 *
 * [build] is the one place a [ReceiptDoc] becomes ESC/POS. The lines come from [ReceiptLayout], the
 * same layout the POS preview and the demo printer use; this class only adds what is specific to the
 * wire (code page 858, ESC @ / ESC t / ESC d / GS V) and replaces what the printer cannot print.
 * `EscPosPrinter` sends [bytes] to the printer and `MockPrinter` shows [preview]; both are produced
 * from the same lines, so the demo's on-screen receipt cannot drift from what the real driver would
 * put on paper. The format has NOT been validated on a physical printer.
 */
internal class EscPosReceipt private constructor(val bytes: ByteArray, val preview: String) {

    companion object {
        private const val ESC = 0x1B
        private const val GS = 0x1D
        private const val LF = 0x0A
        private const val CODE_PAGE_858 = 19
        private const val FEED_LINES = 4

        fun build(doc: ReceiptDoc, paper: PaperWidth): EscPosReceipt {
            val lines = ReceiptLayout.render(doc, paper).map(::toPaperText)

            val out = ArrayList<Byte>()
            fun command(vararg values: Int) = values.forEach { out += it.toByte() }
            command(ESC, 0x40) // initialise
            command(ESC, 0x74, CODE_PAGE_858) // character table
            lines.forEach { line ->
                line.forEach { out += encode(it) }
                command(LF)
            }
            command(ESC, 0x64, FEED_LINES) // feed
            command(GS, 0x56, 0x01) // partial cut
            return EscPosReceipt(out.toByteArray(), lines.joinToString("\n"))
        }

        // The accented letters Spanish needs, in code page 858 (CP437 plus the euro sign).
        private val cp858 = mapOf(
            'ü' to 0x81, 'é' to 0x82, 'á' to 0xA0, 'í' to 0xA1, 'ó' to 0xA2, 'ú' to 0xA3,
            'ñ' to 0xA4, 'Ñ' to 0xA5, '¿' to 0xA8, '¡' to 0xAD, 'É' to 0x90, 'Ü' to 0x9A,
            'Á' to 0xB5, 'Í' to 0xD6, 'Ó' to 0xE0, 'Ú' to 0xE9, '€' to 0xD5,
            // `º` of "Venta N.º 0001".
            'º' to 0xA7,
        )

        private fun encodable(char: Char) = char.code in 0x20..0x7E || char in cp858

        /** What the printer can actually print: anything it cannot becomes `?`, in the preview too. */
        private fun toPaperText(line: String): String = line.map { if (encodable(it)) it else '?' }.joinToString("")

        private fun encode(char: Char): Byte = (cp858[char] ?: char.code).toByte()
    }
}
