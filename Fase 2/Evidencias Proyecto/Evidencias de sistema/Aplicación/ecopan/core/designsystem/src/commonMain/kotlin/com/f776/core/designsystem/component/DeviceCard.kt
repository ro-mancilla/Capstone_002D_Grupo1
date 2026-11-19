package com.f776.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.f776.core.designsystem.theme.EcopanSpacing
import com.f776.core.designsystem.theme.EcopanTheme

/**
 * One card per piece of hardware (scale, printer, drawer, terminal). A simulated device always
 * reads the same way: honey fill, dashed border and the [SimulatedChip] in the title row. Real
 * devices put their own state (a [StatusPill], say) in [status]; a device that is deliberately
 * absent ("Sin impresora") passes `simulated = false` and shows nothing there.
 */
@Composable
fun EcopanDeviceCard(
    title: String,
    icon: ImageVector,
    simulated: Boolean,
    modifier: Modifier = Modifier,
    status: @Composable () -> Unit = { if (simulated) SimulatedChip() },
    content: @Composable ColumnScope.() -> Unit,
) {
    EcopanCard(
        modifier = modifier.fillMaxWidth(),
        tone = if (simulated) EcopanCardTone.Simulated else EcopanCardTone.Default,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(EcopanSpacing.space3)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(EcopanSpacing.space2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, contentDescription = null, tint = EcopanTheme.colors.inkMuted, modifier = Modifier.size(20.dp))
                Text(title, style = EcopanTheme.typography.subtitle, color = EcopanTheme.colors.inkStrong, modifier = Modifier.weight(1f))
                status()
            }
            content()
        }
    }
}
