package com.f776.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.f776.core.designsystem.component.ReceiptColumns
import com.f776.core.designsystem.component.ReceiptText
import com.f776.core.designsystem.theme.DarkColors
import com.f776.core.designsystem.theme.EcopanColors
import com.f776.core.designsystem.theme.EcopanMotion
import com.f776.core.designsystem.theme.LightColors
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesignTokensTest {

    private fun contrast(a: Color, b: Color): Double {
        val l1 = a.luminance().toDouble()
        val l2 = b.luminance().toDouble()
        return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)
    }

    private fun EcopanColors.textPairs(): List<Triple<String, Color, Color>> = listOf(
        Triple("ink body on card", inkBody, card),
        Triple("ink strong on page", inkStrong, page),
        Triple("ink muted on card", inkMuted, card),
        Triple("ink subtle on card", inkSubtle, card),
        Triple("price on card", price, card),
        Triple("white on crust", Color.White, crust),
        Triple("on peach on peach", onPeach, peach),
        Triple("success on its bg", success.fg, success.bg),
        Triple("warning on its bg", warning.fg, warning.bg),
        Triple("danger on its bg", danger.fg, danger.bg),
        Triple("info on its bg", info.fg, info.bg),
        Triple("honey ink on honey 1", honeyInk, honey1),
        Triple("on dark on surface dark", onSurfaceDark, surfaceDark),
        Triple("paper ink on paper", paperInk, paper),
    )

    @Test
    fun textPairsMeetWcagAaInBothThemes() {
        for ((theme, colors) in listOf("light" to LightColors, "dark" to DarkColors)) {
            for ((name, fg, bg) in colors.textPairs()) {
                val ratio = contrast(fg, bg)
                assertTrue(ratio >= 4.5, "$theme: $name is $ratio:1, needs 4.5")
            }
        }
    }

    @Test
    fun controlBorderMeetsNonTextContrastOnCard() {
        assertTrue(contrast(LightColors.controlBorder, LightColors.card) >= 3.0)
        assertTrue(contrast(DarkColors.controlBorder, DarkColors.card) >= 3.0)
    }

    @Test
    fun approvedDriftValues() {
        assertEquals(Color(0xFF7A4A22), LightColors.crust)
        assertEquals(Color(0xFFFBF1E8), LightColors.crustSoft)
        assertEquals(Color(0xFFA5541F), LightColors.price)
        assertEquals(Color(0xFFFBF8F5), LightColors.page)
        assertEquals(Color(0xFF1E1815), DarkColors.page)
        assertEquals(Color(0xFF272019), DarkColors.card)
        assertEquals(Color(0xFF9A5F2C), DarkColors.crust)
        assertEquals(Color(0xFFF0A375), DarkColors.price)
        assertEquals(Color(0xFFD95F19), DarkColors.chart1)
        assertEquals(Color(0xFF2891D5), DarkColors.chart2)
        assertEquals(Color(0xFF847567), DarkColors.controlBorder)
    }

    @Test
    fun chartPaletteHasFiveDistinctSeries() {
        assertEquals(5, LightColors.charts.size)
        assertEquals(5, LightColors.charts.toSet().size)
        assertEquals(5, DarkColors.charts.toSet().size)
    }

    @Test
    fun reducedMotionCollapsesEveryDurationToZero() {
        val reduced = EcopanMotion(reduced = true)
        assertEquals(0, reduced.instantMillis)
        assertEquals(0, reduced.fastMillis)
        assertEquals(0, reduced.baseMillis)
        assertEquals(0, reduced.slowMillis)
        val normal = EcopanMotion(reduced = false)
        assertEquals(80, normal.instantMillis)
        assertEquals(140, normal.fastMillis)
        assertEquals(200, normal.baseMillis)
        assertEquals(320, normal.slowMillis)
    }

    @Test
    fun receiptLinesAreExactlyTheColumnWidth() {
        for (columns in ReceiptColumns.entries) {
            assertEquals(columns.characters, ReceiptText.row("Total", "\$6.294", columns).length)
            assertEquals(columns.characters, ReceiptText.rule(columns).length)
            assertTrue(ReceiptText.centered("Gracias", columns).length <= columns.characters)
        }
        assertEquals(32, ReceiptColumns.Mm58.characters)
        assertEquals(48, ReceiptColumns.Mm80.characters)
    }

    @Test
    fun receiptRowTruncatesLeftBeforeOverflowing() {
        val line = ReceiptText.row("Pan de Hoja Integral con Semillas de Zapallo", "\$12.345", ReceiptColumns.Mm58)
        assertEquals(32, line.length)
        assertTrue(line.endsWith("\$12.345"))
    }
}
