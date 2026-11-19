package com.f776.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.f776.core.designsystem.theme.EcopanShapes
import com.f776.core.designsystem.theme.EcopanTheme

/**
 * One column of an [EcopanDataTable]. [numeric] columns are end-aligned and set in tabular figures
 * so decimals line up. [text] formats a row's cell; the table does no formatting of its own.
 */
@Immutable
class EcopanColumn<T>(
    val header: String,
    val text: (T) -> String,
    val weight: Float = 1f,
    val numeric: Boolean = false,
)

/**
 * A simple table for short, in-memory lists: a sunken header row, 52dp body rows, hairline rules.
 * For long lists build a `LazyColumn` of rows yourself; this does not virtualise. [empty] is shown
 * instead of the body when [rows] is empty.
 */
@Composable
fun <T> EcopanDataTable(
    columns: List<EcopanColumn<T>>,
    rows: List<T>,
    modifier: Modifier = Modifier,
    empty: String? = null,
) {
    val c = EcopanTheme.colors
    Column(modifier = modifier.fillMaxWidth().clip(EcopanShapes.large).background(c.card)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(c.sunken).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (column in columns) {
                Text(
                    column.header,
                    style = EcopanTheme.typography.eyebrow,
                    color = c.inkMuted,
                    textAlign = if (column.numeric) TextAlign.End else TextAlign.Start,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(column.weight),
                )
            }
        }
        if (rows.isEmpty() && empty != null) {
            Text(empty, style = EcopanTheme.typography.body, color = c.inkMuted, modifier = Modifier.padding(16.dp))
        }
        rows.forEach { row ->
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
            Row(
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (column in columns) {
                    Text(
                        column.text(row),
                        style = if (column.numeric) EcopanTheme.typography.figure else EcopanTheme.typography.body,
                        color = c.inkBody,
                        textAlign = if (column.numeric) TextAlign.End else TextAlign.Start,
                        modifier = Modifier.weight(column.weight),
                    )
                }
            }
        }
    }
}
