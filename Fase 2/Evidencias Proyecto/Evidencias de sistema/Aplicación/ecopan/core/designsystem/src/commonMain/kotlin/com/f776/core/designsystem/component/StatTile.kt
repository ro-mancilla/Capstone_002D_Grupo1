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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.f776.core.designsystem.theme.EcopanShapes
import com.f776.core.designsystem.theme.EcopanTheme

/**
 * A KPI card: an eyebrow [label], the [value] as a 32sp tabular figure with its [unit], and an
 * optional [delta] chip ("+41 kg de masa") with its [deltaNote] ("frente a los 7 días anteriores").
 * Values arrive formatted; the tile does no arithmetic.
 */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    icon: ImageVector? = null,
    delta: String? = null,
    deltaNote: String? = null,
) {
    val c = EcopanTheme.colors
    val typography = EcopanTheme.typography
    EcopanCard(modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) Icon(icon, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(20.dp))
            Text(label, style = typography.eyebrow, color = c.inkMuted)
        }
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(value, style = typography.numeric, color = c.inkStrong, maxLines = 1)
            if (unit != null) {
                Text(unit, style = typography.subtitle.copy(fontSize = 20.sp), color = c.inkMuted, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
        if (delta != null) {
            Row(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    delta,
                    style = typography.eyebrow.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
                    color = c.inkStrong,
                    modifier = Modifier.background(c.inset, EcopanShapes.pill).padding(horizontal = 10.dp, vertical = 4.dp),
                )
                if (deltaNote != null) Text(deltaNote, style = typography.bodySmall, color = c.inkMuted)
            }
        }
    }
}
