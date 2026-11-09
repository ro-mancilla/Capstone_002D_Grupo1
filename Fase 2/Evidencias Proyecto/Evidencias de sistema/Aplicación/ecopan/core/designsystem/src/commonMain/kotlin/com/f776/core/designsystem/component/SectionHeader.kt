package com.f776.core.designsystem.component

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.f776.core.designsystem.theme.EcopanTheme

/**
 * The one style of an in-page section heading (settings categories, batch-entry sections) so screens
 * stop hand-rolling weight and colour. Carries no padding: the hosting layout decides spacing.
 */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = EcopanTheme.typography.label.copy(fontWeight = FontWeight.SemiBold),
        color = EcopanTheme.colorScheme.onPrimaryContainer,
        modifier = modifier,
    )
}
