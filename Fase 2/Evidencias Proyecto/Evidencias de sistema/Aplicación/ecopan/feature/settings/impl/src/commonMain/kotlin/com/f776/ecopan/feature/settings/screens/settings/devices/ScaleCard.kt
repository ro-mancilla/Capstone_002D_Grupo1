@file:OptIn(ExperimentalLayoutApi::class)

package com.f776.ecopan.feature.settings.screens.settings.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.f776.core.designsystem.component.EcopanButton
import com.f776.core.designsystem.component.EcopanButtonVariant
import com.f776.core.designsystem.component.EcopanDeviceCard
import com.f776.core.designsystem.component.EcopanFilterChip
import com.f776.core.designsystem.component.EcopanIconButton
import com.f776.core.designsystem.component.EcopanIconButtonVariant
import com.f776.core.designsystem.component.EcopanOutlinedButton
import com.f776.core.designsystem.component.EcopanSegmentedSelector
import com.f776.core.designsystem.component.EcopanSelect
import com.f776.core.designsystem.component.EcopanTextButton
import com.f776.core.designsystem.component.EcopanTextField
import com.f776.core.designsystem.component.SimulatedChip
import com.f776.core.designsystem.component.StatusPill
import com.f776.core.designsystem.component.StatusTone
import com.f776.core.designsystem.icon.EcopanIcons
import com.f776.core.designsystem.theme.EcopanShapes
import com.f776.core.designsystem.theme.EcopanSpacing
import com.f776.core.designsystem.theme.EcopanTheme
import com.f776.ecopan.domain.hardware.SerialFraming
import com.f776.ecopan.domain.hardware.SerialPortInfo
import ecopan.feature.settings.impl.generated.resources.Res
import ecopan.feature.settings.impl.generated.resources.scale_advanced_reset
import ecopan.feature.settings.impl.generated.resources.scale_advanced_title
import ecopan.feature.settings.impl.generated.resources.scale_baud_label
import ecopan.feature.settings.impl.generated.resources.scale_brand_cas
import ecopan.feature.settings.impl.generated.resources.scale_brand_label
import ecopan.feature.settings.impl.generated.resources.scale_brand_other
import ecopan.feature.settings.impl.generated.resources.scale_brand_toledo
import ecopan.feature.settings.impl.generated.resources.scale_chip_check_frame
import ecopan.feature.settings.impl.generated.resources.scale_chip_connecting
import ecopan.feature.settings.impl.generated.resources.scale_chip_connecting_body
import ecopan.feature.settings.impl.generated.resources.scale_chip_no_data
import ecopan.feature.settings.impl.generated.resources.scale_chip_no_data_action
import ecopan.feature.settings.impl.generated.resources.scale_chip_no_data_body
import ecopan.feature.settings.impl.generated.resources.scale_chip_no_ports
import ecopan.feature.settings.impl.generated.resources.scale_chip_no_ports_action
import ecopan.feature.settings.impl.generated.resources.scale_chip_no_ports_body
import ecopan.feature.settings.impl.generated.resources.scale_chip_port_busy
import ecopan.feature.settings.impl.generated.resources.scale_chip_port_busy_action
import ecopan.feature.settings.impl.generated.resources.scale_chip_port_busy_body
import ecopan.feature.settings.impl.generated.resources.scale_chip_responds
import ecopan.feature.settings.impl.generated.resources.scale_chip_simulated
import ecopan.feature.settings.impl.generated.resources.scale_chip_simulated_body
import ecopan.feature.settings.impl.generated.resources.scale_delimiter_label
import ecopan.feature.settings.impl.generated.resources.scale_draft_discarded
import ecopan.feature.settings.impl.generated.resources.scale_framing_label
import ecopan.feature.settings.impl.generated.resources.scale_frame_label
import ecopan.feature.settings.impl.generated.resources.scale_manual_hint
import ecopan.feature.settings.impl.generated.resources.scale_mode_manual
import ecopan.feature.settings.impl.generated.resources.scale_mode_serial
import ecopan.feature.settings.impl.generated.resources.scale_mode_simulated
import ecopan.feature.settings.impl.generated.resources.scale_poll_command_label
import ecopan.feature.settings.impl.generated.resources.scale_poll_interval_label
import ecopan.feature.settings.impl.generated.resources.scale_port_label
import ecopan.feature.settings.impl.generated.resources.scale_port_placeholder
import ecopan.feature.settings.impl.generated.resources.scale_probe_broken
import ecopan.feature.settings.impl.generated.resources.scale_probe_label
import ecopan.feature.settings.impl.generated.resources.scale_probe_nothing
import ecopan.feature.settings.impl.generated.resources.scale_probe_placeholder
import ecopan.feature.settings.impl.generated.resources.scale_probe_read
import ecopan.feature.settings.impl.generated.resources.scale_probe_read_stable
import ecopan.feature.settings.impl.generated.resources.scale_probe_read_unstable
import ecopan.feature.settings.impl.generated.resources.scale_protocol_continuous
import ecopan.feature.settings.impl.generated.resources.scale_protocol_label
import ecopan.feature.settings.impl.generated.resources.scale_protocol_poll
import ecopan.feature.settings.impl.generated.resources.scale_rescan_cd
import ecopan.feature.settings.impl.generated.resources.scale_stability_count_label
import ecopan.feature.settings.impl.generated.resources.scale_stable_values_label
import ecopan.feature.settings.impl.generated.resources.scale_summary
import ecopan.feature.settings.impl.generated.resources.scale_test_last_text
import ecopan.feature.settings.impl.generated.resources.scale_test_no_text
import ecopan.feature.settings.impl.generated.resources.scale_test_responds
import ecopan.feature.settings.impl.generated.resources.scale_test_stable
import ecopan.feature.settings.impl.generated.resources.scale_test_stabilizing
import ecopan.feature.settings.impl.generated.resources.scale_test_title
import ecopan.feature.settings.impl.generated.resources.scale_test_waiting
import ecopan.feature.settings.impl.generated.resources.scale_title
import ecopan.feature.settings.impl.generated.resources.scale_tolerance_label
import ecopan.feature.settings.impl.generated.resources.scale_units_label
import ecopan.feature.settings.impl.generated.resources.scale_window_label
import org.jetbrains.compose.resources.stringResource

/**
 * The "Balanza" card of Settings → Dispositivos: mode, brand, port, live test and the collapsed
 * "Avanzado" section. It edits a draft (see [DevicesViewModel]); nothing here reaches the running
 * driver until the bar's "Aplicar y probar".
 */
@Composable
internal fun ScaleCard(state: DevicesUiState, onAction: (DevicesAction) -> Unit, modifier: Modifier = Modifier) {
    val c = EcopanTheme.colors
    val modeLabels = ScaleModeOption.entries.associateWith { it.label() }
    EcopanDeviceCard(
        title = stringResource(Res.string.scale_title),
        icon = EcopanIcons.Scale,
        simulated = state.connection == ScaleConnection.Simulated,
        modifier = modifier,
        status = { ConnectionPill(state.connection) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(EcopanSpacing.space4)) {

            EcopanSegmentedSelector(
                options = ScaleModeOption.entries,
                selected = state.draft.mode,
                onSelect = { onAction(DevicesAction.SelectMode(it)) },
                label = { modeLabels.getValue(it) },
                modifier = Modifier.fillMaxWidth(),
            )

            state.notice?.let {
                Text(stringResource(Res.string.scale_draft_discarded), style = EcopanTheme.typography.bodySmall, color = c.inkMuted)
            }

            when (state.draft.mode) {
                ScaleModeOption.Manual ->
                    Text(stringResource(Res.string.scale_manual_hint), style = EcopanTheme.typography.body, color = c.inkMuted)

                ScaleModeOption.Simulated -> Unit

                ScaleModeOption.Serial -> SerialBasics(state, onAction)
            }

            if (state.applied.mode != ScaleModeOption.Manual) {
                LiveTestPanel(state)
            }
            ConnectionNote(state, onAction)

            if (state.draft.mode == ScaleModeOption.Serial) {
                AdvancedSection(state, onAction)
            }
        }
    }
}

@Composable
private fun ConnectionPill(connection: ScaleConnection) {
    when (connection) {
        ScaleConnection.Manual -> Unit
        ScaleConnection.Connecting -> StatusPill(stringResource(Res.string.scale_chip_connecting), StatusTone.Info, icon = EcopanIcons.Refresh)
        ScaleConnection.Responding -> StatusPill(stringResource(Res.string.scale_chip_responds), StatusTone.Success)
        ScaleConnection.NoData -> StatusPill(stringResource(Res.string.scale_chip_no_data), StatusTone.Danger)
        ScaleConnection.PortBusy -> StatusPill(stringResource(Res.string.scale_chip_port_busy), StatusTone.Danger)
        ScaleConnection.NoPorts -> StatusPill(stringResource(Res.string.scale_chip_no_ports), StatusTone.Neutral)
        ScaleConnection.Simulated -> SimulatedChip(label = stringResource(Res.string.scale_chip_simulated))
    }
}

@Composable
private fun ScaleModeOption.label(): String = stringResource(
    when (this) {
        ScaleModeOption.Manual -> Res.string.scale_mode_manual
        ScaleModeOption.Serial -> Res.string.scale_mode_serial
        ScaleModeOption.Simulated -> Res.string.scale_mode_simulated
    },
)

// ---- Serial: brand, summary, port, basics -------------------------------------------------------

@Composable
private fun SerialBasics(state: DevicesUiState, onAction: (DevicesAction) -> Unit) {
    val c = EcopanTheme.colors
    val serial = state.draft.serial
    val protocolLabels = listOf(SerialDraft.CONTINUOUS, SerialDraft.POLL).associateWith { protocolPhrase(it) }
    Column(verticalArrangement = Arrangement.spacedBy(EcopanSpacing.space2)) {
        Text(stringResource(Res.string.scale_brand_label), style = EcopanTheme.typography.label, color = c.inkMuted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(EcopanSpacing.chipGap), verticalArrangement = Arrangement.spacedBy(EcopanSpacing.chipGap)) {
            ScaleBrand.entries.forEach { brand ->
                EcopanFilterChip(
                    selected = serial.brand == brand,
                    onClick = { onAction(DevicesAction.SelectBrand(brand)) },
                    label = brand.label(),
                )
            }
        }
        Text(summaryLine(serial), style = EcopanTheme.typography.bodySmall, color = c.inkMuted)
    }

    if (serial.brand == ScaleBrand.Other) {
        Row(horizontalArrangement = Arrangement.spacedBy(EcopanSpacing.space3), modifier = Modifier.fillMaxWidth()) {
            EcopanTextField(
                value = serial.baud,
                onValueChange = { onAction(DevicesAction.EditField(ScaleField.Baud, it)) },
                label = stringResource(Res.string.scale_baud_label),
                isError = ScaleField.Baud in state.errors,
                supportingText = state.errors[ScaleField.Baud]?.message(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            EcopanSelect(
                options = SerialFraming.entries,
                selected = serial.framing,
                onSelect = { onAction(DevicesAction.SelectFraming(it)) },
                optionLabel = { it.label },
                label = stringResource(Res.string.scale_framing_label),
                modifier = Modifier.weight(1f),
            )
            EcopanSelect(
                options = listOf(SerialDraft.CONTINUOUS, SerialDraft.POLL),
                selected = serial.protocol,
                onSelect = { onAction(DevicesAction.SelectProtocol(it)) },
                optionLabel = { protocolLabels.getValue(it) },
                label = stringResource(Res.string.scale_protocol_label),
                modifier = Modifier.weight(2f),
            )
        }
    }

    PortRow(state, onAction)
}

@Composable
private fun PortRow(state: DevicesUiState, onAction: (DevicesAction) -> Unit) {
    val serial = state.draft.serial
    // The saved port may be unplugged right now; keep it selectable so the form shows what is
    // configured rather than silently swapping it for the first port that is present.
    val known = state.ports.firstOrNull { it.name == serial.port }
    val options = if (serial.port.isBlank() || known != null) state.ports else listOf(SerialPortInfo(serial.port, "")) + state.ports
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(EcopanSpacing.space2),
        verticalAlignment = Alignment.Bottom,
    ) {
        EcopanSelect(
            options = options,
            selected = options.firstOrNull { it.name == serial.port },
            onSelect = { onAction(DevicesAction.EditField(ScaleField.Port, it.name)) },
            optionLabel = { it.label },
            label = stringResource(Res.string.scale_port_label),
            placeholder = stringResource(Res.string.scale_port_placeholder),
            isError = ScaleField.Port in state.errors,
            supportingText = state.errors[ScaleField.Port]?.message(),
            modifier = Modifier.weight(1f),
        )
        EcopanIconButton(
            onClick = { onAction(DevicesAction.RescanPorts) },
            icon = EcopanIcons.Refresh,
            contentDescription = stringResource(Res.string.scale_rescan_cd),
            variant = EcopanIconButtonVariant.Outlined,
        )
    }
}

@Composable
private fun ScaleBrand.label(): String = stringResource(
    when (this) {
        ScaleBrand.Cas -> Res.string.scale_brand_cas
        ScaleBrand.Toledo -> Res.string.scale_brand_toledo
        ScaleBrand.Other -> Res.string.scale_brand_other
    },
)

@Composable
private fun protocolPhrase(protocol: String): String = stringResource(
    if (protocol == SerialDraft.POLL) Res.string.scale_protocol_poll else Res.string.scale_protocol_continuous,
)

/** `9600 baudios · 8N1 · la balanza envía el peso sola`. */
@Composable
private fun summaryLine(serial: SerialDraft): String =
    stringResource(Res.string.scale_summary, serial.baud.ifBlank { "?" }, serial.framing.label, protocolPhrase(serial.protocol))

// ---- Live test ---------------------------------------------------------------------------------

@Composable
private fun LiveTestPanel(state: DevicesUiState) {
    val c = EcopanTheme.colors
    val live = state.live
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.sunken, EcopanShapes.large)
            .padding(horizontal = EcopanSpacing.space4, vertical = EcopanSpacing.space3),
        verticalArrangement = Arrangement.spacedBy(EcopanSpacing.space2),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.scale_test_title), style = EcopanTheme.typography.label.copy(fontWeight = FontWeight.SemiBold), color = c.inkStrong, modifier = Modifier.weight(1f))
            if (state.connection == ScaleConnection.Responding) {
                Icon(EcopanIcons.CheckCircle, contentDescription = null, tint = c.success.fg, modifier = Modifier.size(14.dp))
                Text(
                    stringResource(Res.string.scale_test_responds),
                    style = EcopanTheme.typography.bodySmall,
                    color = c.success.fg,
                    modifier = Modifier.padding(start = EcopanSpacing.space1),
                )
            }
        }
        val grams = live?.grams
        if (grams == null) {
            Text(stringResource(Res.string.scale_test_waiting), style = EcopanTheme.typography.body, color = c.inkMuted)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(EcopanSpacing.space2), verticalAlignment = Alignment.Bottom) {
                Text(formatKg(grams), style = EcopanTheme.typography.numeric, color = if (live.stable) c.inkStrong else c.inkMuted)
                Text(
                    stringResource(if (live.stable) Res.string.scale_test_stable else Res.string.scale_test_stabilizing),
                    style = EcopanTheme.typography.bodySmall,
                    color = if (live.stable) c.success.fg else c.inkMuted,
                    modifier = Modifier.padding(bottom = EcopanSpacing.space1),
                )
            }
        }
        Text(
            live?.rawText?.let { stringResource(Res.string.scale_test_last_text, it) } ?: stringResource(Res.string.scale_test_no_text),
            style = EcopanTheme.typography.ledger,
            color = c.inkBody,
            modifier = Modifier
                .fillMaxWidth()
                .background(c.card, EcopanShapes.small)
                .padding(horizontal = EcopanSpacing.space3, vertical = EcopanSpacing.space2),
        )
    }
}

/** What the chip means and the one thing to do about it (copy deck 4c). */
@Composable
private fun ConnectionNote(state: DevicesUiState, onAction: (DevicesAction) -> Unit) {
    val port = state.applied.serial.port
    val (body, action) = when (state.connection) {
        ScaleConnection.Connecting -> stringResource(Res.string.scale_chip_connecting_body) to null
        ScaleConnection.NoData ->
            stringResource(Res.string.scale_chip_no_data_body, port) to (stringResource(Res.string.scale_chip_no_data_action) to DevicesAction.RetryConnection)
        ScaleConnection.PortBusy ->
            stringResource(Res.string.scale_chip_port_busy_body, port) to (stringResource(Res.string.scale_chip_port_busy_action) to DevicesAction.RescanPorts)
        ScaleConnection.NoPorts ->
            stringResource(Res.string.scale_chip_no_ports_body) to (stringResource(Res.string.scale_chip_no_ports_action) to DevicesAction.RescanPorts)
        ScaleConnection.Simulated -> stringResource(Res.string.scale_chip_simulated_body) to null
        ScaleConnection.Manual, ScaleConnection.Responding -> return
    }
    Column(verticalArrangement = Arrangement.spacedBy(EcopanSpacing.space1)) {
        Text(body, style = EcopanTheme.typography.bodySmall, color = EcopanTheme.colors.inkBody)
        if (action != null) {
            EcopanTextButton(text = action.first, onClick = { onAction(action.second) })
        }
    }
}

// ---- Advanced ----------------------------------------------------------------------------------

@Composable
private fun AdvancedSection(state: DevicesUiState, onAction: (DevicesAction) -> Unit) {
    val c = EcopanTheme.colors
    val serial = state.draft.serial
    Column(verticalArrangement = Arrangement.spacedBy(EcopanSpacing.space3)) {
        EcopanButton(
            text = stringResource(Res.string.scale_advanced_title),
            onClick = { onAction(DevicesAction.ToggleAdvanced) },
            variant = EcopanButtonVariant.Text,
            trailingIcon = EcopanIcons.ExpandMore,
        )
        if (!state.advancedExpanded) return@Column

        if (ScaleField.FrameRegex in state.errors) {
            StatusPill(stringResource(Res.string.scale_chip_check_frame), StatusTone.Danger)
        }
        EcopanTextField(
            value = serial.delimiter,
            onValueChange = { onAction(DevicesAction.EditField(ScaleField.Delimiter, it)) },
            label = stringResource(Res.string.scale_delimiter_label),
            isError = ScaleField.Delimiter in state.errors,
            supportingText = state.errors[ScaleField.Delimiter]?.message(),
            textStyle = EcopanTheme.typography.ledger,
            modifier = Modifier.fillMaxWidth(),
        )
        EcopanTextField(
            value = serial.frameRegex,
            onValueChange = { onAction(DevicesAction.EditField(ScaleField.FrameRegex, it)) },
            label = stringResource(Res.string.scale_frame_label),
            isError = ScaleField.FrameRegex in state.errors,
            supportingText = state.errors[ScaleField.FrameRegex]?.message(),
            textStyle = EcopanTheme.typography.ledger,
            modifier = Modifier.fillMaxWidth(),
        )
        EcopanTextField(
            value = serial.unitFactors,
            onValueChange = { onAction(DevicesAction.EditField(ScaleField.UnitFactors, it)) },
            label = stringResource(Res.string.scale_units_label),
            isError = ScaleField.UnitFactors in state.errors,
            supportingText = state.errors[ScaleField.UnitFactors]?.message(),
            textStyle = EcopanTheme.typography.ledger,
            modifier = Modifier.fillMaxWidth(),
        )
        EcopanTextField(
            value = serial.stableValues,
            onValueChange = { onAction(DevicesAction.EditField(ScaleField.StableValues, it)) },
            label = stringResource(Res.string.scale_stable_values_label),
            isError = ScaleField.StableValues in state.errors,
            supportingText = state.errors[ScaleField.StableValues]?.message(),
            textStyle = EcopanTheme.typography.ledger,
            modifier = Modifier.fillMaxWidth(),
        )
        if (serial.protocol == SerialDraft.POLL) {
            EcopanTextField(
                value = serial.pollCommand,
                onValueChange = { onAction(DevicesAction.EditField(ScaleField.PollCommand, it)) },
                label = stringResource(Res.string.scale_poll_command_label),
                isError = ScaleField.PollCommand in state.errors,
                supportingText = state.errors[ScaleField.PollCommand]?.message(),
                textStyle = EcopanTheme.typography.ledger,
                modifier = Modifier.fillMaxWidth(),
            )
            NumberField(ScaleField.PollInterval, serial.pollIntervalMs, Res.string.scale_poll_interval_label, state, onAction, Modifier.fillMaxWidth())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(EcopanSpacing.space3), modifier = Modifier.fillMaxWidth()) {
            NumberField(ScaleField.StabilityCount, serial.stabilityCount, Res.string.scale_stability_count_label, state, onAction, Modifier.weight(1f))
            NumberField(ScaleField.ToleranceGrams, serial.toleranceGrams, Res.string.scale_tolerance_label, state, onAction, Modifier.weight(1f))
            NumberField(ScaleField.WindowMs, serial.windowMs, Res.string.scale_window_label, state, onAction, Modifier.weight(1f))
        }
        EcopanOutlinedButton(text = stringResource(Res.string.scale_advanced_reset), onClick = { onAction(DevicesAction.ResetAdvanced) })

        EcopanTextField(
            value = state.probeInput,
            onValueChange = { onAction(DevicesAction.ProbeInputChanged(it)) },
            label = stringResource(Res.string.scale_probe_label),
            placeholder = stringResource(Res.string.scale_probe_placeholder),
            textStyle = EcopanTheme.typography.ledger,
            modifier = Modifier.fillMaxWidth().testTag("scale_probe_input"),
        )
        state.probe?.let { result ->
            Text(
                text = when (result) {
                    is ProbeResult.Read -> when (result.stable) {
                        true -> stringResource(Res.string.scale_probe_read_stable, formatKg(result.grams))
                        false -> stringResource(Res.string.scale_probe_read_unstable, formatKg(result.grams))
                        null -> stringResource(Res.string.scale_probe_read, formatKg(result.grams))
                    }
                    ProbeResult.NothingFound -> stringResource(Res.string.scale_probe_nothing)
                    ProbeResult.FrameBroken -> stringResource(Res.string.scale_probe_broken)
                },
                style = EcopanTheme.typography.bodySmall,
                color = if (result is ProbeResult.Read) c.success.fg else c.inkBody,
            )
        }
    }
}

@Composable
private fun NumberField(
    field: ScaleField,
    value: String,
    label: org.jetbrains.compose.resources.StringResource,
    state: DevicesUiState,
    onAction: (DevicesAction) -> Unit,
    modifier: Modifier,
) {
    EcopanTextField(
        value = value,
        onValueChange = { onAction(DevicesAction.EditField(field, it)) },
        label = stringResource(label),
        isError = field in state.errors,
        supportingText = state.errors[field]?.message(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
internal fun ScaleFieldError.message(): String {
    val copy = copy()
    return stringResource(copy.resource, *copy.args.toTypedArray())
}
