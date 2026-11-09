package com.f776.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.f776.core.designsystem.theme.EcopanShapes
import com.f776.core.designsystem.theme.EcopanSpacing
import com.f776.core.designsystem.theme.EcopanTheme
import com.f776.core.designsystem.theme.collectInteractionFlags
import com.f776.core.designsystem.theme.stateFill
import ecopan.core.designsystem.generated.resources.Res
import ecopan.core.designsystem.generated.resources.unapplied_apply
import ecopan.core.designsystem.generated.resources.unapplied_discard
import org.jetbrains.compose.resources.stringResource

/**
 * The fixed bottom bar for settings that only take effect on apply: a dark surface, the [message]
 * ("Cambios sin aplicar en la balanza"), an outlined "Descartar" and a peach "Aplicar y probar".
 * With [applyEnabled] false (hidden errors), say why in [message]; the apply button fades to 40%.
 */
@Composable
fun EcopanUnappliedChangesBar(
    message: String,
    onDiscard: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
    applyEnabled: Boolean = true,
    discardLabel: String = stringResource(Res.string.unapplied_discard),
    applyLabel: String = stringResource(Res.string.unapplied_apply),
) {
    val c = EcopanTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(c.surfaceDark)
            .defaultMinSize(minHeight = 72.dp)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            message,
            style = EcopanTheme.typography.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
            color = c.onSurfaceDark,
            modifier = Modifier.weight(1f),
        )
        BarPill(discardLabel, onDiscard, enabled = true, fill = Color.Transparent, hover = c.onSurfaceDark.copy(alpha = 0.08f), ink = c.onSurfaceDark, border = c.onSurfaceDark.copy(alpha = 0.14f), minHeight = EcopanSpacing.controlMin)
        BarPill(applyLabel, onApply, enabled = applyEnabled, fill = c.peach, hover = c.peachHover, ink = c.onPeach, border = null, minHeight = 48.dp)
    }
}

@Composable
private fun BarPill(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    fill: Color,
    hover: Color,
    ink: Color,
    border: Color?,
    minHeight: androidx.compose.ui.unit.Dp,
) {
    val c = EcopanTheme.colors
    val shape = EcopanShapes.pill
    val interaction = remember { MutableInteractionSource() }
    val flags = interaction.collectInteractionFlags()
    val bg by animateColorAsState(stateFill(fill, hover, hover, flags, enabled), animationSpec = EcopanTheme.motion.fast(), label = "barPill")
    Text(
        text = label,
        style = EcopanTheme.typography.label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
        color = ink,
        maxLines = 1,
        modifier = Modifier
            .disabledAlpha(enabled)
            .focusRing(flags.focused, shape, c.focusRing)
            .background(bg, shape)
            .then(if (border != null) Modifier.border(BorderStroke(1.dp, border), shape) else Modifier)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = minHeight)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    )
}
