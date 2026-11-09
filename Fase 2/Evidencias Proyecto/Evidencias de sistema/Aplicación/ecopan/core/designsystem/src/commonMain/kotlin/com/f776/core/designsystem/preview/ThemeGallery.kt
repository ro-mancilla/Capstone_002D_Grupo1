package com.f776.core.designsystem.preview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.f776.core.designsystem.theme.EcopanShapes
import com.f776.core.designsystem.theme.EcopanTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ThemeGallery() {
    val c = EcopanTheme.colors
    GallerySection("Colores") {
        val swatches = listOf(
            "crust" to c.crust, "crust soft" to c.crustSoft, "peach" to c.peach, "price" to c.price,
            "page" to c.page, "card" to c.card, "sunken" to c.sunken, "inset" to c.inset,
            "ink strong" to c.inkStrong, "ink muted" to c.inkMuted, "control" to c.controlBorder,
            "éxito" to c.success.fg, "atención" to c.warning.fg, "error" to c.danger.fg, "info" to c.info.fg,
            "honey" to c.honey3, "chart 1" to c.chart1, "chart 2" to c.chart2, "chart 3" to c.chart3,
            "chart 4" to c.chart4, "chart 5" to c.chart5,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for ((name, color) in swatches) Swatch(name, color)
        }
    }
    GallerySection("Tipografía") {
        val t = EcopanTheme.typography
        Text("Display 52", style = t.display, color = c.inkStrong)
        Text("Headline 32", style = t.headline, color = c.inkStrong)
        Text("Title 26", style = t.title, color = c.inkStrong)
        Text("Subtitle 18", style = t.subtitle, color = c.inkStrong)
        Text("Body 16: Marraqueta, hallulla y pan amasado", style = t.body, color = c.inkBody)
        Text("Body small 14", style = t.bodySmall, color = c.inkMuted)
        Text("Label 14", style = t.label, color = c.inkBody)
        Text("Eyebrow 12", style = t.eyebrow, color = c.inkMuted)
        Text("0,842 kg · 1.234.567", style = t.numeric, color = c.inkStrong)
        Text("\$3.200/kg", style = t.figure, color = c.price)
        Text("ST,GS, 0.842 kg", style = t.ledger, color = c.inkBody)
    }
    GallerySection("Formas") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(
                "8" to EcopanShapes.small, "12" to EcopanShapes.medium, "16" to EcopanShapes.large,
                "22" to EcopanShapes.extraLarge, "30" to EcopanShapes.dialog, "pill" to EcopanShapes.pill,
            ).forEach { (name, shape) ->
                Box(
                    Modifier.size(width = 72.dp, height = 48.dp).background(c.crustSoft, shape).border(BorderStroke(1.dp, c.crustLine), shape),
                ) { Text(name, style = EcopanTheme.typography.eyebrow, color = c.inkBody, modifier = Modifier) }
            }
        }
    }
}

@Composable
private fun Swatch(name: String, color: Color) {
    val c = EcopanTheme.colors
    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(width = 88.dp, height = 40.dp).background(color, EcopanShapes.small).border(BorderStroke(1.dp, c.line), EcopanShapes.small))
        Text(name, style = EcopanTheme.typography.eyebrow, color = c.inkMuted)
    }
}
