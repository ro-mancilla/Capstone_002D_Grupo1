package com.f776.core.designsystem.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.f776.core.designsystem.component.EcopanAlert
import com.f776.core.designsystem.component.EcopanCard
import com.f776.core.designsystem.component.EcopanCardTone
import com.f776.core.designsystem.component.EcopanColumn
import com.f776.core.designsystem.component.EcopanDataTable
import com.f776.core.designsystem.component.EcopanDeviceCard
import com.f776.core.designsystem.component.EcopanModalSurface
import com.f776.core.designsystem.component.EcopanUnappliedChangesBar
import com.f776.core.designsystem.component.HintMessage
import com.f776.core.designsystem.component.ProductTile
import com.f776.core.designsystem.component.ReceiptColumns
import com.f776.core.designsystem.component.ReceiptPaper
import com.f776.core.designsystem.component.ReceiptText
import com.f776.core.designsystem.component.ScaleDriver
import com.f776.core.designsystem.component.ScaleReadout
import com.f776.core.designsystem.component.ScaleState
import com.f776.core.designsystem.component.SimulatedChip
import com.f776.core.designsystem.component.StatTile
import com.f776.core.designsystem.component.SummaryTile
import com.f776.core.designsystem.component.StatusTone
import com.f776.core.designsystem.component.SyncIndicatorState
import com.f776.core.designsystem.component.SyncStatusIndicator
import com.f776.core.designsystem.icon.EcopanIcons
import com.f776.core.designsystem.theme.EcopanTheme

/** Sample receipt lines, built with [ReceiptText] so the gallery shows exactly what a printer gets. */
internal fun sampleReceipt(columns: ReceiptColumns): List<String> = listOf(
    ReceiptText.centered("Panadería El Sol", columns),
    ReceiptText.centered("Sucursal Principal", columns),
    ReceiptText.rule(columns),
    "07-10-2026 08:42",
    ReceiptText.rule(columns),
    "Marraqueta",
    ReceiptText.row("  0,842 kg @ \$3.200/kg", "\$2.694", columns),
    "Empanada de Pino",
    ReceiptText.row("  2 @ \$1.800", "\$3.600", columns),
    ReceiptText.rule(columns),
    ReceiptText.row("Total", "\$6.294", columns),
    ReceiptText.row("Efectivo", "\$10.000", columns),
    ReceiptText.row("Vuelto", "\$3.706", columns),
    ReceiptText.rule(columns),
    "Venta N.º 0001",
    ReceiptText.centered("Gracias por su compra", columns),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DomainGallery() {
    val gap = Arrangement.spacedBy(16.dp)
    GallerySection("Sincronización") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SyncStatusIndicator(SyncIndicatorState.OnlineIdle, {})
            SyncStatusIndicator(SyncIndicatorState.Syncing, {})
            SyncStatusIndicator(SyncIndicatorState.OfflinePending(2), {})
            SyncStatusIndicator(SyncIndicatorState.Attention(1), {})
        }
    }
    GallerySection("Balanza") {
        FlowRow(horizontalArrangement = gap, verticalArrangement = gap) {
            ScaleReadout("0,880", "kg", ScaleState.Stabilizing, ScaleDriver.Simulated, Modifier.width(420.dp), label = "Peso", price = "\$2.816")
            ScaleReadout("0,842", "kg", ScaleState.Stable, ScaleDriver.Real, Modifier.width(420.dp), label = "Peso", price = "\$2.694")
            ScaleReadout("1,250", "kg", ScaleState.Stable, ScaleDriver.Manual, Modifier.width(420.dp), label = "Peso")
        }
    }
    GallerySection("Boleta") {
        FlowRow(horizontalArrangement = gap, verticalArrangement = gap) {
            ReceiptPaper(sampleReceipt(ReceiptColumns.Mm58), simulated = true)
            ReceiptPaper(sampleReceipt(ReceiptColumns.Mm80), columns = ReceiptColumns.Mm80, simulated = false)
        }
    }
    GallerySection("Avisos en línea") {
        EcopanAlert("Venta registrada · 8:42", tone = StatusTone.Success, modifier = Modifier.width(420.dp))
        EcopanAlert(
            "La venta quedó registrada, pero no se pudo imprimir el comprobante.",
            modifier = Modifier.width(420.dp),
            supporting = "Revisa el papel o el cable de la impresora.",
            actionLabel = "Reimprimir",
            onAction = {},
        )
        EcopanAlert("Cajón abierto · simulado, no hay cajón físico", tone = StatusTone.Simulated, icon = EcopanIcons.CashDrawer, modifier = Modifier.width(420.dp))
    }
    GallerySection("Modal ancho") {
        EcopanModalSurface(Modifier.width(420.dp)) { Text("Boleta", style = EcopanTheme.typography.title, modifier = Modifier.padding(28.dp)) }
    }
    GallerySection("Dispositivo simulado") {
        EcopanDeviceCard("Cajón de dinero", EcopanIcons.CashDrawer, simulated = true, modifier = Modifier.width(420.dp)) {
            Text("Cajón simulado (demo). No abre ningún cajón físico.", style = EcopanTheme.typography.body)
        }
        EcopanDeviceCard("Impresora", EcopanIcons.Print, simulated = false, modifier = Modifier.width(420.dp)) {
            Text("Sin impresora: la boleta se muestra en pantalla. No es un dispositivo simulado.", style = EcopanTheme.typography.body)
        }
    }
    GallerySection("Barra de cambios") {
        EcopanUnappliedChangesBar("Cambios sin aplicar en la balanza · la conexión se reinicia al aplicar", {}, {})
        EcopanUnappliedChangesBar("2 campos por corregir en Avanzado", {}, {}, applyEnabled = false)
    }
    GallerySection("Productos y cifras") {
        FlowRow(horizontalArrangement = gap, verticalArrangement = gap) {
            ProductTile("Marraqueta", "\$3.200/kg", {}, Modifier.width(200.dp), badge = "2")
            ProductTile("Empanada de Pino", "\$1.800", {}, Modifier.width(200.dp))
            StatTile("Masa mezclada", "460,5", Modifier.width(300.dp), unit = "kg de masa", icon = EcopanIcons.Inventory, delta = "+41 kg de masa", deltaNote = "frente a los 7 días anteriores")
        }
        Row(horizontalArrangement = gap, modifier = Modifier.width(620.dp)) {
            SummaryTile("Efectivo", "\$5.000", Modifier.weight(1f))
            SummaryTile("Tarjeta", "\$3.507", Modifier.weight(1f))
            SummaryTile("Transferencia", "\$1.200", Modifier.weight(1f))
            SummaryTile("Total", "\$9.707", Modifier.weight(1f), emphasized = true)
        }
        EcopanDataTable(
            columns = listOf(
                EcopanColumn<Pair<String, String>>("Producto", { it.first }, weight = 2f),
                EcopanColumn("Kg de masa", { it.second }, numeric = true),
            ),
            rows = listOf("Empanada de Pino" to "90", "Pan Amasado" to "87", "Hallulla" to "84"),
        )
        HintMessage(icon = EcopanIcons.SearchOff, title = "Sin resultados", subtitle = "Prueba con otra búsqueda.")
    }
}
