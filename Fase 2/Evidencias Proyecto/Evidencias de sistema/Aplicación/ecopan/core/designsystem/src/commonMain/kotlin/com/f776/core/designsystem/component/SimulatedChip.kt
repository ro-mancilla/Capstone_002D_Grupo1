package com.f776.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.unit.dp
import com.f776.core.designsystem.icon.EcopanIcons
import com.f776.core.designsystem.theme.EcopanShapes
import com.f776.core.designsystem.theme.EcopanTheme
import ecopan.core.designsystem.generated.resources.Res
import ecopan.core.designsystem.generated.resources.status_simulated
import org.jetbrains.compose.resources.stringResource

/**
 * Marks anything that is not real: a simulated scale, a test receipt. Honey fill with a dashed
 * honey border -- the dashes carry the meaning for people who cannot tell the colours apart.
 */
@Composable
fun SimulatedChip(
    modifier: Modifier = Modifier,
    label: String = stringResource(Res.string.status_simulated),
) {
    val c = EcopanTheme.colors
    val shape = EcopanShapes.pill
    Row(
        modifier = modifier
            .background(c.honey1, shape)
            .drawBehind {
                val stroke = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                )
                drawOutline(shape.createOutline(size, layoutDirection, this), c.honey3, style = stroke)
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(EcopanIcons.Simulated, contentDescription = null, tint = c.honeyInk, modifier = Modifier.size(16.dp))
        Text(label, style = EcopanTheme.typography.label, color = c.honeyInk, maxLines = 1)
    }
}
