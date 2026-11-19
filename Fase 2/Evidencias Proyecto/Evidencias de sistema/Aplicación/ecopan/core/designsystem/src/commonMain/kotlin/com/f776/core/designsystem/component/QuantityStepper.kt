package com.f776.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.f776.core.designsystem.icon.EcopanIcons
import com.f776.core.designsystem.theme.EcopanShapes
import com.f776.core.designsystem.theme.EcopanSpacing
import com.f776.core.designsystem.theme.EcopanTheme
import ecopan.core.designsystem.generated.resources.Res
import ecopan.core.designsystem.generated.resources.stepper_decrease_cd
import ecopan.core.designsystem.generated.resources.stepper_increase_cd
import org.jetbrains.compose.resources.stringResource

/**
 * Minus, a tabular value, plus. Stateless: the caller owns [value] and clamps it to [range]; the
 * buttons disable at the ends. [touch] makes it the 56dp till control.
 */
@Composable
fun EcopanQuantityStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: IntRange = 0..Int.MAX_VALUE,
    step: Int = 1,
    enabled: Boolean = true,
    touch: Boolean = false,
) {
    val c = EcopanTheme.colors
    Row(
        modifier = modifier
            .background(c.inset, EcopanShapes.pill)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EcopanIconButton(
            onClick = { onValueChange((value - step).coerceAtLeast(range.first)) },
            icon = EcopanIcons.Remove,
            contentDescription = stringResource(Res.string.stepper_decrease_cd),
            variant = EcopanIconButtonVariant.Tonal,
            touch = touch,
            enabled = enabled && value - step >= range.first,
        )
        Text(
            text = value.toString(),
            style = EcopanTheme.typography.figure,
            color = c.inkStrong,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.defaultMinSize(minWidth = if (touch) EcopanSpacing.touch else EcopanSpacing.controlMin),
        )
        EcopanIconButton(
            onClick = { onValueChange((value + step).coerceAtMost(range.last)) },
            icon = EcopanIcons.Add,
            contentDescription = stringResource(Res.string.stepper_increase_cd),
            variant = EcopanIconButtonVariant.Filled,
            touch = touch,
            enabled = enabled && value + step <= range.last,
        )
    }
}
