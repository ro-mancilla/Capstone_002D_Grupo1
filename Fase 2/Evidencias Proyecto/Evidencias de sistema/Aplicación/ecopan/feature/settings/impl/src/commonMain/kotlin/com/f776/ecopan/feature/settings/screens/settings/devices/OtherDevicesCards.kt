package com.f776.ecopan.feature.settings.screens.settings.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.f776.core.designsystem.component.EcopanDeviceCard
import com.f776.core.designsystem.component.EcopanFilterChip
import com.f776.core.designsystem.component.EcopanSelect
import com.f776.core.designsystem.component.EcopanUnappliedChangesBar
import com.f776.core.designsystem.icon.EcopanIcons
import com.f776.core.designsystem.theme.EcopanSpacing
import com.f776.core.designsystem.theme.EcopanTheme
import com.f776.ecopan.domain.hardware.DriverDescriptor
import com.f776.ecopan.domain.hardware.DriverIds
import com.f776.ecopan.domain.hardware.PaperWidth
import ecopan.feature.settings.impl.generated.resources.Res
import ecopan.feature.settings.impl.generated.resources.drawer_hint
import ecopan.feature.settings.impl.generated.resources.drawer_simulated_text
import ecopan.feature.settings.impl.generated.resources.drawer_simulated_with_printer_text
import ecopan.feature.settings.impl.generated.resources.drawer_title
import ecopan.feature.settings.impl.generated.resources.drawer_via_printer_text
import ecopan.feature.settings.impl.generated.resources.drawer_via_printer_unavailable_text
import ecopan.feature.settings.impl.generated.resources.printer_hint
import ecopan.feature.settings.impl.generated.resources.printer_paper_58
import ecopan.feature.settings.impl.generated.resources.printer_paper_80
import ecopan.feature.settings.impl.generated.resources.printer_paper_label
import ecopan.feature.settings.impl.generated.resources.printer_service_default
import ecopan.feature.settings.impl.generated.resources.printer_service_hint
import ecopan.feature.settings.impl.generated.resources.printer_service_label
import ecopan.feature.settings.impl.generated.resources.printer_none_text
import ecopan.feature.settings.impl.generated.resources.printer_title
import ecopan.feature.settings.impl.generated.resources.scale_bar_hidden_errors
import ecopan.feature.settings.impl.generated.resources.scale_bar_unapplied
import ecopan.feature.settings.impl.generated.resources.scale_bar_visible_errors
import ecopan.feature.settings.impl.generated.resources.terminal_hint
import ecopan.feature.settings.impl.generated.resources.terminal_simulated_text
import ecopan.feature.settings.impl.generated.resources.terminal_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Printer, cash drawer and payment terminal: the right-hand column of Settings → Dispositivos. */
@Composable
internal fun OtherDevicesCards(state: DevicesUiState, onAction: (DevicesAction) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(EcopanSpacing.cardGap)) {
        PrinterCard(state.printer, onAction)
        DeviceCard(
            title = stringResource(Res.string.drawer_title),
            icon = EcopanIcons.CashDrawer,
            device = state.drawer,
            text = stringResource(
                when (state.drawer.text) {
                    DeviceText.Simulated -> Res.string.drawer_simulated_text
                    DeviceText.Real -> Res.string.drawer_hint
                    DeviceText.DrawerViaPrinter -> Res.string.drawer_via_printer_text
                    DeviceText.DrawerViaPrinterUnavailable -> Res.string.drawer_via_printer_unavailable_text
                    DeviceText.DrawerSimulatedWithRealPrinter -> Res.string.drawer_simulated_with_printer_text
                },
            ),
            onSelect = { onAction(DevicesAction.SelectDrawer(it)) },
        )
        DeviceCard(
            title = stringResource(Res.string.terminal_title),
            icon = EcopanIcons.CreditCard,
            device = state.terminal,
            text = stringResource(if (state.terminal.simulated) Res.string.terminal_simulated_text else Res.string.terminal_hint),
            onSelect = { onAction(DevicesAction.SelectTerminal(it)) },
        )
    }
}

@Composable
private fun PrinterCard(printer: PrinterUi, onAction: (DevicesAction) -> Unit) {
    val c = EcopanTheme.colors
    EcopanDeviceCard(
        title = stringResource(Res.string.printer_title),
        icon = EcopanIcons.Print,
        simulated = printer.simulated,
        modifier = Modifier.fillMaxWidth(),
    ) {
        DriverSelect(printer.drivers, printer.selectedId) { onAction(DevicesAction.SelectPrinter(it)) }
        Column(verticalArrangement = Arrangement.spacedBy(EcopanSpacing.space2)) {
            Text(stringResource(Res.string.printer_paper_label), style = EcopanTheme.typography.label, color = c.inkMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(EcopanSpacing.chipGap)) {
                PaperWidth.entries.forEach { width ->
                    EcopanFilterChip(
                        selected = printer.paperWidth == width,
                        onClick = { onAction(DevicesAction.SelectPaperWidth(width)) },
                        label = stringResource(if (width == PaperWidth.Mm58) Res.string.printer_paper_58 else Res.string.printer_paper_80),
                    )
                }
            }
            // The OS print service the `escpos` driver prints to (the drawer kick goes through the same
            // printer). Only the JVM till finds any; the simulated printer has no use for it.
            if (!printer.simulated && printer.selectedId == DriverIds.ESCPOS_PRINTER) {
                val defaultLabel = stringResource(Res.string.printer_service_default)
                val services = printer.printServices.let { found ->
                    if (printer.printServiceName.isBlank() || printer.printServiceName in found) found else found + printer.printServiceName
                }
                EcopanSelect(
                    options = listOf("") + services,
                    selected = printer.printServiceName,
                    onSelect = { onAction(DevicesAction.SelectPrintService(it)) },
                    optionLabel = { it.ifBlank { defaultLabel } },
                    label = stringResource(Res.string.printer_service_label),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(Res.string.printer_service_hint), style = EcopanTheme.typography.bodySmall, color = c.inkMuted)
            }
        }
        Text(
            stringResource(if (printer.none) Res.string.printer_none_text else Res.string.printer_hint),
            style = EcopanTheme.typography.bodySmall,
            color = c.inkMuted,
        )
    }
}

/** A drawer or terminal. The simulated look comes from [EcopanDeviceCard], the same as the scale and the printer. */
@Composable
private fun DeviceCard(
    title: String,
    icon: ImageVector,
    device: SimpleDeviceUi,
    text: String,
    onSelect: (String) -> Unit,
) {
    EcopanDeviceCard(title = title, icon = icon, simulated = device.simulated, modifier = Modifier.fillMaxWidth()) {
        if (device.drivers.size > 1) DriverSelect(device.drivers, device.selectedId, onSelect)
        Text(text, style = EcopanTheme.typography.bodySmall, color = EcopanTheme.colors.inkBody)
    }
}

@Composable
private fun DriverSelect(drivers: List<DriverDescriptor>, selectedId: String, onSelect: (String) -> Unit) {
    EcopanSelect(
        options = drivers,
        selected = drivers.firstOrNull { it.id == selectedId },
        onSelect = { onSelect(it.id) },
        optionLabel = { it.displayName },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The fixed bar under the settings: says what is pending, and why "Aplicar y probar" is off when it is. */
@Composable
internal fun DevicesApplyBar(state: DevicesUiState, onAction: (DevicesAction) -> Unit, modifier: Modifier = Modifier) {
    val bar = state.bar ?: return
    val message = when (bar) {
        ApplyBar.Unapplied -> stringResource(Res.string.scale_bar_unapplied)
        is ApplyBar.HiddenErrors -> pluralStringResource(Res.plurals.scale_bar_hidden_errors, bar.count, bar.count)
        is ApplyBar.VisibleErrors -> pluralStringResource(Res.plurals.scale_bar_visible_errors, bar.count, bar.count)
    }
    EcopanUnappliedChangesBar(
        message = message,
        onDiscard = { onAction(DevicesAction.Discard) },
        onApply = { onAction(DevicesAction.Apply) },
        applyEnabled = state.canApply,
        modifier = modifier,
    )
}
