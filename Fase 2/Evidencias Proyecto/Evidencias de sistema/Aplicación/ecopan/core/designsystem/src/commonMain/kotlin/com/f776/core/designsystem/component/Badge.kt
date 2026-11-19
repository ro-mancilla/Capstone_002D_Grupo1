package com.f776.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.f776.core.designsystem.theme.EcopanShapes
import com.f776.core.designsystem.theme.EcopanTheme

enum class EcopanBadgeTone { Crust, Peach, Neutral }

/**
 * A small count or short label on top of something else (a tab, a product tile). It is not a
 * status: a status uses [StatusPill], which always carries an icon and a word.
 */
@Composable
fun EcopanBadge(
    text: String,
    modifier: Modifier = Modifier,
    tone: EcopanBadgeTone = EcopanBadgeTone.Crust,
) {
    val c = EcopanTheme.colors
    val (fill, content) = when (tone) {
        EcopanBadgeTone.Crust -> c.crust to c.inkInverse
        EcopanBadgeTone.Peach -> c.peach to c.onPeach
        EcopanBadgeTone.Neutral -> c.inset to c.inkBody
    }
    Text(
        text = text,
        style = EcopanTheme.typography.eyebrow.copy(fontSize = 11.sp, lineHeight = 14.sp),
        color = content,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = modifier
            .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
            .background(fill, EcopanShapes.pill)
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}
