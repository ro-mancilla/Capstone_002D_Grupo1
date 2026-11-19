package com.f776.core.designsystem.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.f776.core.designsystem.component.EcopanAccentButton
import com.f776.core.designsystem.component.EcopanBadge
import com.f776.core.designsystem.component.EcopanBadgeTone
import com.f776.core.designsystem.component.EcopanButton
import com.f776.core.designsystem.component.EcopanButtonSize
import com.f776.core.designsystem.component.EcopanCard
import com.f776.core.designsystem.component.EcopanCardTone
import com.f776.core.designsystem.component.EcopanCheckbox
import com.f776.core.designsystem.component.EcopanFilterChip
import com.f776.core.designsystem.component.EcopanIconButton
import com.f776.core.designsystem.component.EcopanIconButtonVariant
import com.f776.core.designsystem.component.EcopanListItem
import com.f776.core.designsystem.component.EcopanOutlinedButton
import com.f776.core.designsystem.component.EcopanQuantityStepper
import com.f776.core.designsystem.component.EcopanRadioButton
import com.f776.core.designsystem.component.EcopanSegmentedSelector
import com.f776.core.designsystem.component.EcopanSelect
import com.f776.core.designsystem.component.EcopanSwitch
import com.f776.core.designsystem.component.EcopanTabs
import com.f776.core.designsystem.component.EcopanTag
import com.f776.core.designsystem.component.EcopanTextButton
import com.f776.core.designsystem.component.EcopanTextField
import com.f776.core.designsystem.component.EcopanTonalButton
import com.f776.core.designsystem.component.EcopanTooltip
import com.f776.core.designsystem.component.EcopanToast
import com.f776.core.designsystem.component.EcopanToastTone
import com.f776.core.designsystem.component.SectionHeader
import com.f776.core.designsystem.component.SimulatedChip
import com.f776.core.designsystem.component.EcopanAlert
import com.f776.core.designsystem.component.StatusPill
import com.f776.core.designsystem.component.StatusTone
import com.f776.core.designsystem.icon.EcopanIcons
import com.f776.core.designsystem.theme.EcopanTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ControlsGallery() {
    val gap = Arrangement.spacedBy(12.dp)
    GallerySection("Botones") {
        FlowRow(horizontalArrangement = gap, verticalArrangement = gap) {
            EcopanButton("Guardar", {}, leadingIcon = EcopanIcons.Check)
            EcopanTonalButton("Tonal", {})
            EcopanOutlinedButton("Outlined", {})
            EcopanTextButton("Texto", {})
            EcopanButton("Deshabilitado", {}, enabled = false)
        }
        FlowRow(horizontalArrangement = gap, verticalArrangement = gap) {
            EcopanButton("Agregar 0,842 kg", {}, size = EcopanButtonSize.Touch)
            EcopanAccentButton("Cobrar", {})
        }
        FlowRow(horizontalArrangement = gap, verticalArrangement = gap) {
            EcopanIconButton({}, EcopanIcons.Settings, "Ajustes")
            EcopanIconButton({}, EcopanIcons.Refresh, "Buscar dispositivos de nuevo", variant = EcopanIconButtonVariant.Tonal)
            EcopanIconButton({}, EcopanIcons.Add, "Agregar", variant = EcopanIconButtonVariant.Filled, touch = true)
            EcopanIconButton({}, EcopanIcons.Close, "Cerrar", variant = EcopanIconButtonVariant.Outlined)
        }
    }
    GallerySection("Estados y etiquetas") {
        FlowRow(horizontalArrangement = gap, verticalArrangement = gap) {
            StatusPill("Correcto", StatusTone.Success)
            StatusPill("Stock bajo", StatusTone.Warning)
            StatusPill("Revisar saldo", StatusTone.Danger)
            StatusPill("Modelo entrenado", StatusTone.Info)
            StatusPill("Sin conexión", StatusTone.Neutral)
            StatusPill("Simulado", StatusTone.Simulated)
            SimulatedChip()
        }
        FlowRow(horizontalArrangement = gap, verticalArrangement = gap) {
            EcopanBadge("3")
            EcopanBadge("Nuevo", tone = EcopanBadgeTone.Peach)
            EcopanBadge("12", tone = EcopanBadgeTone.Neutral)
            EcopanTag("Sucursal Principal", icon = EcopanIcons.Inventory)
            var selected by remember { mutableStateOf(true) }
            EcopanFilterChip(selected, { selected = !selected }, "Hallulla")
            EcopanFilterChip(!selected, { selected = !selected }, "Berlín")
        }
    }
    GallerySection("Campos y selección") {
        var text by remember { mutableStateOf("") }
        var choice by remember { mutableStateOf<String?>(null) }
        var mode by remember { mutableStateOf("Balanza serial") }
        var on by remember { mutableStateOf(true) }
        var checked by remember { mutableStateOf(true) }
        var radio by remember { mutableStateOf(true) }
        var quantity by remember { mutableIntStateOf(2) }
        EcopanTextField(text, { text = it }, label = "Nombre del producto", placeholder = "Marraqueta", modifier = Modifier.width(360.dp))
        EcopanTextField("abc", {}, label = "Con error", supportingText = "Ingresa un número entero entre 50 y 4.000.000.", isError = true, modifier = Modifier.width(360.dp))
        EcopanSelect(listOf("COM3", "COM4"), choice, { choice = it }, { it }, label = "Puerto", placeholder = "Elige un puerto", modifier = Modifier.width(360.dp))
        EcopanSegmentedSelector(
            options = listOf("Ingreso manual", "Balanza serial", "Simulada"),
            selected = mode,
            onSelect = { mode = it },
            label = { it },
            modifier = Modifier.width(520.dp),
        )
        FlowRow(horizontalArrangement = gap, verticalArrangement = gap) {
            EcopanSwitch(on, { on = it })
            EcopanSwitch(false, {}, enabled = false)
            EcopanCheckbox(checked, { checked = it })
            EcopanRadioButton(radio, { radio = !radio })
            EcopanRadioButton(!radio, { radio = !radio })
            EcopanQuantityStepper(quantity, { quantity = it }, range = 0..9)
            EcopanQuantityStepper(quantity, { quantity = it }, range = 0..9, touch = true)
        }
    }
    GallerySection("Superficies y navegación") {
        FlowRow(horizontalArrangement = gap, verticalArrangement = gap) {
            EcopanCard(modifier = Modifier.width(260.dp)) { Text("Tarjeta", style = EcopanTheme.typography.subtitle, color = EcopanTheme.colors.inkStrong) }
            EcopanCard(modifier = Modifier.width(260.dp), tone = EcopanCardTone.Sunken) { Text("Hundida", color = EcopanTheme.colors.inkBody) }
            EcopanCard(modifier = Modifier.width(260.dp), tone = EcopanCardTone.Accent, onClick = {}) { Text("Destacada", color = EcopanTheme.colors.inkBody) }
            EcopanCard(modifier = Modifier.width(260.dp), tone = EcopanCardTone.Outlined) { Text("Con línea", color = EcopanTheme.colors.inkBody) }
        }
        var tab by remember { mutableIntStateOf(0) }
        EcopanTabs(tab, listOf("Hoy", "7 días", "30 días"), { tab = it })
        EcopanCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            SectionHeader("Insumos", modifier = Modifier.padding(16.dp))
            EcopanListItem("Azúcar", supporting = "Reponer en 3 kg", trailing = { StatusPill("Revisar saldo", StatusTone.Danger) }, divider = true)
            EcopanListItem("Harina", supporting = "Reponer en 20 kg", onClick = {}, trailing = { Text("194,048 kg", style = EcopanTheme.typography.figure, color = EcopanTheme.colors.inkStrong) })
        }
        EcopanAlert("Azúcar quedó bajo cero. Avisa a tu gerente para que registre la compra.")
        FlowRow(horizontalArrangement = gap, verticalArrangement = gap) {
            EcopanToast("Lote de Marraqueta listo · 12 kg de masa", supporting = "Sucursal Principal", icon = EcopanIcons.Inventory, actionLabel = "Ver producción", onAction = {}, onDismiss = {})
            EcopanToast("Lote de Marraqueta listo · 12 kg de masa", tone = EcopanToastTone.Success, icon = EcopanIcons.CheckCircle, onDismiss = {})
            EcopanTooltip("Calculado con un modelo entrenado con el historial de ventas") {
                StatusPill("Modelo entrenado", StatusTone.Info)
            }
        }
    }
}
