package com.f776.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.f776.core.designsystem.theme.EcopanShapes
import com.f776.core.designsystem.theme.EcopanSpacing
import com.f776.core.designsystem.theme.EcopanTheme

/**
 * An inline message that stays on screen: a tinted band with an icon, a [message], an optional
 * [supporting] line and an optional action. Unlike [EcopanToast] it fills its parent and does not go
 * away on its own; the sentence itself says what to do. Used for a negative stock balance (Danger),
 * "Venta registrada" (Success), a failed print (Warning, with a reprint action) and "Cajón abierto ·
 * simulado" ([StatusTone.Simulated], dashed). Status is carried by the icon and the words, never the
 * tint alone.
 *
 * Danger and Warning are announced to screen readers when they appear (`role=alert`); the calmer
 * tones are announced politely.
 */
@Composable
fun EcopanAlert(
    message: String,
    modifier: Modifier = Modifier,
    tone: StatusTone = StatusTone.Danger,
    icon: ImageVector = tone.defaultIcon,
    supporting: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = EcopanTheme.colors.toneColors(tone)
    val dashed = tone == StatusTone.Simulated
    val shape = EcopanShapes.large
    val live = if (tone == StatusTone.Danger || tone == StatusTone.Warning) LiveRegionMode.Assertive else LiveRegionMode.Polite
    val hasDetail = supporting != null || actionLabel != null
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = live }
            .background(colors.bg, shape)
            .then(
                if (dashed) {
                    Modifier.drawBehind {
                        val stroke = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
                        drawOutline(shape.createOutline(size, layoutDirection, this), colors.line, style = stroke)
                    }
                } else {
                    Modifier.border(BorderStroke(1.dp, colors.line), shape)
                },
            )
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = EcopanSpacing.space4, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(EcopanSpacing.space3),
        verticalAlignment = if (hasDetail) Alignment.Top else Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.fg, modifier = Modifier.size(EcopanSpacing.space5))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(EcopanSpacing.space1)) {
            Text(
                message,
                style = EcopanTheme.typography.body.copy(fontWeight = FontWeight.Medium),
                color = colors.fg,
            )
            if (supporting != null) Text(supporting, style = EcopanTheme.typography.bodySmall, color = colors.fg)
            if (actionLabel != null && onAction != null) {
                EcopanButton(text = actionLabel, onClick = onAction, variant = EcopanButtonVariant.Outlined)
            }
        }
    }
}
