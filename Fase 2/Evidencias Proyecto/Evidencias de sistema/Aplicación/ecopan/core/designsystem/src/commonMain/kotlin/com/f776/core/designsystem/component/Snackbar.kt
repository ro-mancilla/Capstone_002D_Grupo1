package com.f776.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.f776.core.designsystem.icon.EcopanIcons
import com.f776.core.designsystem.theme.EcopanElevation
import com.f776.core.designsystem.theme.EcopanShapes
import com.f776.core.designsystem.theme.EcopanSpacing
import com.f776.core.designsystem.theme.EcopanTheme
import com.f776.core.designsystem.theme.ecopanShadow
import ecopan.core.designsystem.generated.resources.Res
import ecopan.core.designsystem.generated.resources.close_notice
import org.jetbrains.compose.resources.stringResource

/** [Dark] is the transient toast; [Success] is the till's dismissible strip that stays until closed. */
enum class EcopanToastTone { Dark, Success }

/**
 * A notice: [message], an optional [supporting] line and [actionLabel]. When [onDismiss] is given it
 * shows a 44dp close button described by [dismissContentDescription] ("Cerrar aviso" by default).
 * Never put an identifier in a notice.
 */
@Composable
fun EcopanToast(
    message: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    icon: ImageVector? = null,
    tone: EcopanToastTone = EcopanToastTone.Dark,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    dismissContentDescription: String = stringResource(Res.string.close_notice),
) {
    val c = EcopanTheme.colors
    val dark = tone == EcopanToastTone.Dark
    val fill = if (dark) c.surfaceDark else c.success.bg
    val ink = if (dark) c.onSurfaceDark else c.success.fg
    val secondary = if (dark) c.onSurfaceDark.copy(alpha = 0.72f) else c.success.fg
    val shape = if (dark) EcopanShapes.large else EcopanShapes.medium
    Row(
        modifier = modifier
            .then(if (dark) Modifier.ecopanShadow(EcopanElevation.Overlay, shape) else Modifier)
            .widthIn(max = 420.dp)
            .background(fill, shape)
            .then(if (dark) Modifier else Modifier.border(BorderStroke(1.dp, c.success.line), shape))
            .defaultMinSize(minHeight = 48.dp)
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = if (onDismiss != null) 4.dp else 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(message, style = EcopanTheme.typography.label, color = ink)
            if (supporting != null) Text(supporting, style = EcopanTheme.typography.bodySmall, color = secondary)
        }
        if (actionLabel != null && onAction != null) {
            Text(
                actionLabel,
                style = EcopanTheme.typography.label,
                color = if (dark) c.peach else c.success.fg,
                modifier = Modifier
                    .defaultMinSize(minHeight = EcopanSpacing.controlMin)
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = 8.dp, vertical = 12.dp),
            )
        }
        if (onDismiss != null) {
            EcopanIconButton(
                onClick = onDismiss,
                icon = EcopanIcons.Close,
                contentDescription = dismissContentDescription,
                variant = EcopanIconButtonVariant.Standard,
            )
        }
    }
}

/** A host that renders each queued [SnackbarData] as an [EcopanToast]; the action and dismiss map straight through. */
@Composable
fun EcopanSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(hostState = hostState, modifier = modifier) { data: SnackbarData ->
        EcopanToast(
            message = data.visuals.message,
            actionLabel = data.visuals.actionLabel,
            onAction = if (data.visuals.actionLabel != null) ({ data.performAction() }) else null,
            onDismiss = if (data.visuals.withDismissAction) ({ data.dismiss() }) else null,
        )
    }
}
