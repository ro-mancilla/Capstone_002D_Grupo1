package com.f776.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.f776.core.designsystem.theme.EcopanElevation
import com.f776.core.designsystem.theme.EcopanTheme
import com.f776.core.designsystem.theme.ecopanShadow
import ecopan.core.designsystem.generated.resources.Res
import ecopan.core.designsystem.generated.resources.receipt_simulated
import org.jetbrains.compose.resources.stringResource

/** Thermal paper widths: 58 mm prints 32 characters per line, 80 mm prints 48. */
enum class ReceiptColumns(val characters: Int, val paperWidth: Dp) {
    Mm58(32, 300.dp),
    Mm80(48, 420.dp),
}

/** Pure text layout of receipt lines, so what the preview shows is exactly what the printer receives. */
object ReceiptText {
    /** [left] and [right] on one line of [columns], the gap filled with spaces; [left] is cut if they collide. */
    fun row(left: String, right: String, columns: ReceiptColumns): String {
        val width = columns.characters
        val gap = width - left.length - right.length
        return if (gap >= 1) left + " ".repeat(gap) + right else left.take((width - right.length - 1).coerceAtLeast(0)) + " " + right
    }

    /** [text] centred on a line of [columns]. */
    fun centered(text: String, columns: ReceiptColumns): String {
        val pad = ((columns.characters - text.length) / 2).coerceAtLeast(0)
        return " ".repeat(pad) + text
    }

    /** A full-width rule of dashes. */
    fun rule(columns: ReceiptColumns): String = "-".repeat(columns.characters)
}

/** A zig-zag bottom edge, like paper torn off the roll. */
class TornEdgeShape(private val toothWidth: Dp = 8.dp, private val toothHeight: Dp = 6.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val tooth = with(density) { toothWidth.toPx() }
        val depth = with(density) { toothHeight.toPx() }
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height - depth)
            var x = size.width
            var down = true
            while (x > 0f) {
                x = (x - tooth / 2f).coerceAtLeast(0f)
                lineTo(x, if (down) size.height else size.height - depth)
                down = !down
            }
            lineTo(0f, size.height - depth)
            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * The receipt preview: fixed white paper (white in dark mode too, because it is a picture of paper),
 * monospaced [lines] of exactly [columns] characters, a torn bottom edge and, when [simulated], a
 * honey strip above and below saying nothing was printed. Build [lines] with [ReceiptText].
 */
@Composable
fun ReceiptPaper(
    lines: List<String>,
    modifier: Modifier = Modifier,
    columns: ReceiptColumns = ReceiptColumns.Mm58,
    simulated: Boolean = false,
    simulatedLabel: String = stringResource(Res.string.receipt_simulated),
) {
    val c = EcopanTheme.colors
    val shape = TornEdgeShape()
    Column(
        modifier = modifier
            .width(columns.paperWidth)
            .ecopanShadow(EcopanElevation.Raised, shape)
            .clip(shape)
            .background(c.paper),
    ) {
        if (simulated) Strip(simulatedLabel)
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), contentAlignment = Alignment.TopCenter) {
            Text(
                text = lines.joinToString("\n"),
                style = EcopanTheme.typography.ledger.copy(fontSize = 11.5.sp, lineHeight = 16.7.sp),
                color = c.paperInk,
                softWrap = false,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            )
        }
        if (simulated) Strip(simulatedLabel)
        Box(Modifier.padding(bottom = 6.dp))
    }
}

@Composable
private fun Strip(label: String) {
    val c = EcopanTheme.colors
    Text(
        text = label,
        style = EcopanTheme.typography.eyebrow.copy(fontWeight = FontWeight.Bold),
        color = c.paperStripInk,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().background(c.paperStrip).padding(vertical = 6.dp),
    )
}
