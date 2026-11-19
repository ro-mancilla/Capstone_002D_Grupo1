package com.f776.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.f776.core.designsystem.theme.EcopanElevation
import com.f776.core.designsystem.theme.EcopanShapes
import com.f776.core.designsystem.theme.EcopanTheme
import com.f776.core.designsystem.theme.ecopanShadow

/**
 * A modal dialog: 30dp radius, card fill, the deepest warm shadow. [confirmButton] sits at the end
 * of the action row; pass at most one filled button, and put the safe choice in [dismissButton].
 */
@Composable
fun EcopanDialog(
    onDismissRequest: () -> Unit,
    title: String,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    dismissButton: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    val c = EcopanTheme.colors
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        Column(
            modifier = modifier
                .widthIn(min = 280.dp, max = 560.dp)
                .ecopanShadow(EcopanElevation.Dialog, EcopanShapes.dialog)
                .clip(EcopanShapes.dialog)
                .background(c.card)
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CompositionLocalProvider(LocalContentColor provides c.inkBody) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) Icon(icon, contentDescription = null, tint = c.crust, modifier = Modifier.size(24.dp))
                    Text(title, style = EcopanTheme.typography.title, color = c.inkStrong)
                }
                content()
                Row(
                    modifier = Modifier.align(Alignment.End),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}

/**
 * The bare surface of a large modal: 30dp radius, card fill, the deepest warm shadow, and no title
 * or padding of its own, so the content lays itself out (the till's receipt is a two-pane modal).
 * Separate from [EcopanModal] so it can be shown inline in the gallery.
 */
@Composable
fun EcopanModalSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = EcopanTheme.colors
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .ecopanShadow(EcopanElevation.Dialog, EcopanShapes.dialog)
            .clip(EcopanShapes.dialog)
            .background(c.card),
    ) {
        CompositionLocalProvider(LocalContentColor provides c.inkBody) { content() }
    }
}

/** A modal over a scrim whose size is the caller's ([modifier]), not the platform default width. */
@Composable
fun EcopanModal(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        EcopanModalSurface(modifier = modifier, content = content)
    }
}
