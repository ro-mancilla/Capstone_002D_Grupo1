package com.f776.ecopan.domain.hardware

/**
 * The one place a [ReceiptDoc] becomes text. Pure, so the on-screen preview, the demo printer and
 * the ESC/POS printer cannot disagree, and so the column widths (32 / 48) are golden-testable.
 * Every returned line is at most `columns` characters; money is CLP (`$6.294`).
 *
 * Nothing is silently cut: a long name or header wraps onto further lines, and when a detail line
 * is too long for its price, the left side is elided with `..` so the price always survives.
 *
 * The ticket identifies the sale by its per-till correlativo (`Venta N.º 0001`). A document with no
 * correlativo ([ReceiptDoc.receiptNumber] = 0) falls back to the whole sale id, so a receipt never
 * goes out without any identifier; a document that has one never prints the UUID.
 */
object ReceiptLayout {

    fun render(doc: ReceiptDoc, width: PaperWidth): List<String> {
        val columns = width.columns
        val rule = "-".repeat(columns)
        val method = doc.paymentMethod.receiptLabel()
        return buildList {
            doc.header.forEach { addAll(wrap(it, columns).map { part -> centered(part, columns) }) }
            add(rule)
            if (doc.issuedAtLabel.isNotEmpty()) {
                addAll(wrap(doc.issuedAtLabel, columns))
                add(rule)
            }
            doc.lines.forEach { line ->
                addAll(wrap(line.name, columns))
                val unit = formatMoney(line.unitPriceMinor) + if (line.perKg) "/kg" else ""
                addAll(twoColumns("  ${line.quantityLabel} @ $unit", formatMoney(line.lineTotalMinor), columns))
            }
            add(rule)
            addAll(twoColumns("Total", formatMoney(doc.totalMinor), columns))
            val tendered = doc.cashTenderedMinor
            val change = doc.changeMinor
            if (tendered != null && change != null) {
                addAll(twoColumns(method, formatMoney(tendered), columns))
                addAll(twoColumns("Vuelto", formatMoney(change), columns))
            } else {
                addAll(twoColumns(method, formatMoney(doc.totalMinor), columns))
            }
            if (doc.receiptNumber > 0) {
                add(rule)
                add(receiptNumberLabel(doc.receiptNumber))
                doc.footer.forEach { addAll(wrap(it, columns).map { part -> centered(part, columns) }) }
            } else {
                add(rule)
                doc.footer.forEach { addAll(wrap(it, columns).map { part -> centered(part, columns) }) }
                add("Venta:")
                addAll(evenChunks(doc.saleId.toString(), columns))
            }
        }
    }

    /** `Venta N.º 0001`: four digits, zero padded, growing past 9999 rather than wrapping. */
    fun receiptNumberLabel(number: Int): String = "Venta N.º ${number.toString().padStart(4, '0')}"

    /** `$11.824`: no decimals, dot-grouped thousands, sign in front (`-$1.234`). */
    fun formatMoney(amountMinor: Long): String {
        val negative = amountMinor < 0
        val digits = if (negative) amountMinor.toString().drop(1) else amountMinor.toString()
        val grouped = digits.reversed().chunked(3).joinToString(".").reversed()
        return (if (negative) "-$" else "$") + grouped
    }

    /**
     * [left] and [right] on one line, [right] flush with the edge. The right side (a price) is
     * never cut: [left] is elided with `..` to make room, and if [right] cannot fit beside even a
     * single character it goes on a line of its own.
     */
    fun twoColumns(left: String, right: String, width: Int): List<String> {
        val room = width - right.length - 1
        if (room < 1) return wrap(left, width) + right.padStart(width)
        val fitted = when {
            left.length <= room -> left
            room <= ELLIPSIS.length -> left.take(room)
            else -> left.take(room - ELLIPSIS.length) + ELLIPSIS
        }
        return listOf(fitted + " ".repeat(room - fitted.length + 1) + right)
    }

    /** Hard-wraps at [width]: a long name goes onto further lines instead of being cut off. */
    private fun wrap(text: String, width: Int): List<String> =
        if (text.isEmpty()) listOf("") else text.chunked(width)

    /** Splits [text] into the fewest lines of at most [width], as even as possible. */
    private fun evenChunks(text: String, width: Int): List<String> {
        val parts = (text.length + width - 1) / width
        return text.chunked((text.length + parts - 1) / parts)
    }

    private fun centered(text: String, columns: Int): String =
        " ".repeat(((columns - text.length) / 2).coerceAtLeast(0)) + text

    private const val ELLIPSIS = ".."
}
