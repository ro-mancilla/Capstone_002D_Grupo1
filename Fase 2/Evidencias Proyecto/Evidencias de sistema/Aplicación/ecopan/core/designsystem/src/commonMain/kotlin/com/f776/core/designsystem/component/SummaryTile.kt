package com.f776.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.f776.core.designsystem.theme.EcopanShapes
import com.f776.core.designsystem.theme.EcopanTheme

/**
 * A compact figure for a row of totals that has to fit several across a dialog (the till's
 * "Efectivo / Tarjeta / Transferencia / Total"); [StatTile] is the full-size KPI card. [emphasized]
 * marks the one figure the others add up to. The value arrives formatted.
 */
@Composable
fun SummaryTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    val c = EcopanTheme.colors
    val shape = EcopanShapes.medium
    Column(
        modifier = modifier
            .background(if (emphasized) c.crustSoft else c.sunken, shape)
            .border(BorderStroke(1.dp, if (emphasized) c.crustLine else c.hairline), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = EcopanTheme.typography.eyebrow, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            value,
            style = EcopanTheme.typography.figure,
            color = if (emphasized) c.price else c.inkStrong,
            maxLines = 1,
        )
    }
}
