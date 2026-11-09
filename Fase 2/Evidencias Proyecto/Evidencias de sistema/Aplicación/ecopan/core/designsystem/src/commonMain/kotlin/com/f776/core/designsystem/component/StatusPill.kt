package com.f776.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.f776.core.designsystem.icon.EcopanIcons
import com.f776.core.designsystem.theme.EcopanColors
import com.f776.core.designsystem.theme.EcopanShapes
import com.f776.core.designsystem.theme.EcopanTheme
import com.f776.core.designsystem.theme.collectInteractionFlags

/** The semantic status of a [StatusPill]. [Simulated] marks data that did not come from the real world. */
enum class StatusTone(internal val defaultIcon: ImageVector) {
    Success(EcopanIcons.CheckCircle),
    Warning(EcopanIcons.Warning),
    Danger(EcopanIcons.Error),
    Info(EcopanIcons.Info),
    Neutral(EcopanIcons.Neutral),
    Simulated(EcopanIcons.Simulated),
}

@Immutable
internal class ToneColors(val fg: Color, val bg: Color, val line: Color)

internal fun EcopanColors.toneColors(tone: StatusTone): ToneColors = when (tone) {
    StatusTone.Success -> ToneColors(success.fg, success.bg, success.line)
    StatusTone.Warning -> ToneColors(warning.fg, warning.bg, warning.line)
    StatusTone.Danger -> ToneColors(danger.fg, danger.bg, danger.line)
    StatusTone.Info -> ToneColors(info.fg, info.bg, info.line)
    StatusTone.Neutral -> ToneColors(inkMuted, inset, line)
    StatusTone.Simulated -> ToneColors(honeyInk, honey1, honey2)
}

/**
 * The one shape of a small status chip: a leading icon, then a word. Status is never carried by
 * colour alone, so the icon defaults per [tone] and [label] is required. Every status in the app
 * renders through this, so shape, padding and type cannot drift per screen.
 *
 * [onClick] turns it into a button (with a 48dp touch target); [onClickLabel] names that action
 * for screen readers.
 */
@Composable
fun StatusPill(
    label: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
    icon: ImageVector = tone.defaultIcon,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
) {
    val colors = EcopanTheme.colors.toneColors(tone)
    val shape = EcopanShapes.pill
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .then(if (onClick != null) Modifier.minimumInteractiveComponentSize() else Modifier)
            .background(colors.bg, shape)
            .border(BorderStroke(1.dp, colors.line), shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Button,
                        onClickLabel = onClickLabel,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.fg, modifier = Modifier.size(16.dp))
        Text(label, style = EcopanTheme.typography.label, color = colors.fg, maxLines = 1)
    }
}
