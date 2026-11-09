@file:OptIn(ExperimentalMaterial3Api::class)

package com.f776.core.designsystem.component

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.f776.core.designsystem.theme.EcopanShapes
import com.f776.core.designsystem.theme.EcopanTheme

/**
 * Shows [text] in a dark tooltip on hover (desktop) or long press (touch) over [content]. Use it to
 * explain, never to carry information that is not visible anywhere else: touch users may never see it.
 */
@Composable
fun EcopanTooltip(
    text: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val c = EcopanTheme.colors
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            PlainTooltip(
                containerColor = c.surfaceDark,
                contentColor = c.onSurfaceDark,
                shape = EcopanShapes.small,
            ) {
                Text(text, style = EcopanTheme.typography.bodySmall)
            }
        },
        state = rememberTooltipState(),
        modifier = modifier,
        content = content,
    )
}
