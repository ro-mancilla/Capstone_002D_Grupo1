package com.f776.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.f776.core.designsystem.theme.EcopanTheme

/** A centred empty or hint state: an icon, a [title] and an optional [subtitle]. */
@Composable
fun HintMessage(modifier: Modifier = Modifier, icon: ImageVector, title: String, subtitle: String = "") {
    val c = EcopanTheme.colors
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(imageVector = icon, contentDescription = null, tint = c.inkSubtle, modifier = Modifier.size(56.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text(title, style = EcopanTheme.typography.subtitle, color = c.inkStrong, textAlign = TextAlign.Center)
        if (subtitle.isNotBlank()) {
            Text(subtitle, style = EcopanTheme.typography.body, color = c.inkMuted, textAlign = TextAlign.Center)
        }
    }
}
