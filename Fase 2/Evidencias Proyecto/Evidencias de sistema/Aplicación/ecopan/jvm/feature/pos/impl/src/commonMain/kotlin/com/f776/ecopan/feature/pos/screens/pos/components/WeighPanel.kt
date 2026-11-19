package com.f776.ecopan.feature.pos.screens.pos.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import com.f776.core.designsystem.component.EcopanButton
import com.f776.core.designsystem.component.EcopanButtonSize
import com.f776.core.designsystem.component.EcopanButtonVariant
import com.f776.core.designsystem.component.EcopanCard
import com.f776.core.designsystem.component.EcopanTextField
import com.f776.core.designsystem.component.ScaleDriver
import com.f776.core.designsystem.component.ScaleReadout
import com.f776.core.designsystem.component.ScaleState
import com.f776.core.designsystem.theme.EcopanSpacing
import com.f776.core.designsystem.theme.EcopanTheme
import com.f776.ecopan.feature.pos.screens.pos.ScaleMode
import com.f776.ecopan.feature.pos.screens.pos.WeighPanelUi
import com.f776.ecopan.feature.pos.screens.pos.WeighSource
import com.f776.ecopan.feature.pos.screens.pos.formatGramsAsKg
import com.f776.ecopan.feature.pos.screens.pos.formatGramsNumber
import com.f776.ecopan.feature.pos.screens.pos.formatMinor
import ecopan.jvm.feature.pos.impl.generated.resources.Res
import ecopan.jvm.feature.pos.impl.generated.resources.pos_add_button
import ecopan.jvm.feature.pos.impl.generated.resources.pos_add_weight
import ecopan.jvm.feature.pos.impl.generated.resources.pos_cancel_weighing
import ecopan.jvm.feature.pos.impl.generated.resources.pos_weigh_add_hint
import ecopan.jvm.feature.pos.impl.generated.resources.pos_weigh_amount
import ecopan.jvm.feature.pos.impl.generated.resources.pos_weigh_manual_hint
import ecopan.jvm.feature.pos.impl.generated.resources.pos_weigh_simulated_chip
import ecopan.jvm.feature.pos.impl.generated.resources.pos_weight_entry_label
import ecopan.jvm.feature.pos.impl.generated.resources.pos_weight_kg_label
import ecopan.jvm.feature.pos.impl.generated.resources.pos_weight_label
import org.jetbrains.compose.resources.stringResource

/**
 * The open weigh session, beside the running sale (spec 4d). The readout names its source (a real
 * scale, a typed weight, or the demo scale with the honey chip), says "Estabilizando…" or "Estable"
 * with an icon as well as the word, and "Agregar" only turns on for a reading that is settled
 * ([WeighPanelUi.canAdd]) -- so a jittering number can never be sold.
 */
@Composable
internal fun WeighPanel(
    panel: WeighPanelUi,
    scaleMode: ScaleMode,
    onManualWeightChanged: (String) -> Unit,
    onAdd: (grams: Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = EcopanTheme.colors
    EcopanCard(modifier = modifier.fillMaxWidth().padding(horizontal = EcopanSpacing.space3, vertical = EcopanSpacing.space2)) {
        Text(panel.productName, style = EcopanTheme.typography.subtitle, color = colors.inkStrong)

        if (panel.isManualEntry) {
            if (scaleMode == ScaleMode.DISCONNECTED) {
                Text(stringResource(Res.string.pos_weigh_manual_hint), style = EcopanTheme.typography.bodySmall, color = colors.inkMuted)
            }
            EcopanTextField(
                value = panel.manualKgText,
                onValueChange = onManualWeightChanged,
                label = stringResource(Res.string.pos_weight_entry_label),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().padding(top = EcopanSpacing.space2).testTag("weigh_manual_input"),
            )
        } else {
            ScaleReadout(
                weight = formatGramsNumber(panel.grams),
                unit = stringResource(Res.string.pos_weight_kg_label),
                state = if (panel.settled) ScaleState.Stable else ScaleState.Stabilizing,
                driver = when (panel.source) {
                    WeighSource.Real -> ScaleDriver.Real
                    WeighSource.Manual -> ScaleDriver.Manual
                    WeighSource.Simulated -> ScaleDriver.Simulated
                },
                label = stringResource(Res.string.pos_weight_label),
                driverLabel = if (panel.source == WeighSource.Simulated) stringResource(Res.string.pos_weigh_simulated_chip) else null,
                modifier = Modifier.padding(top = EcopanSpacing.space3),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = EcopanSpacing.space3),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.pos_weigh_amount, formatMinor(panel.pricePerKgMinor)),
                style = EcopanTheme.typography.label,
                color = colors.inkMuted,
            )
            Text(formatMinor(panel.previewTotalMinor), style = EcopanTheme.typography.numeric, color = colors.price)
        }

        if (!panel.isManualEntry && !panel.stable) {
            Text(
                stringResource(Res.string.pos_weigh_add_hint),
                style = EcopanTheme.typography.bodySmall,
                color = colors.inkMuted,
                modifier = Modifier.padding(top = EcopanSpacing.space2),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = EcopanSpacing.space4),
            horizontalArrangement = Arrangement.spacedBy(EcopanSpacing.space2),
        ) {
            EcopanButton(
                text = stringResource(Res.string.pos_cancel_weighing),
                onClick = onClose,
                variant = EcopanButtonVariant.Outlined,
                size = EcopanButtonSize.Touch,
            )
            EcopanButton(
                text = if (panel.canAdd) stringResource(Res.string.pos_add_weight, formatGramsAsKg(panel.grams)) else stringResource(Res.string.pos_add_button),
                onClick = { onAdd(panel.grams) },
                size = EcopanButtonSize.Touch,
                enabled = panel.canAdd,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
