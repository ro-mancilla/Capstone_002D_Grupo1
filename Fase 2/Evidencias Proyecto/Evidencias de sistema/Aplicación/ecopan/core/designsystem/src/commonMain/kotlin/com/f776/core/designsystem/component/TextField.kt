package com.f776.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.f776.core.designsystem.icon.EcopanIcons
import com.f776.core.designsystem.theme.EcopanShapes
import com.f776.core.designsystem.theme.EcopanSpacing
import com.f776.core.designsystem.theme.EcopanTheme
import ecopan.core.designsystem.generated.resources.Res
import ecopan.core.designsystem.generated.resources.select_open_cd
import org.jetbrains.compose.resources.stringResource

/** The 12dp, control-bordered frame shared by [EcopanTextField] and [EcopanSelect]. */
@Composable
private fun FieldFrame(
    label: String?,
    supportingText: String?,
    isError: Boolean,
    focused: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    field: @Composable () -> Unit,
) {
    val c = EcopanTheme.colors
    Column(modifier = modifier.disabledAlpha(enabled), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) Text(label, style = EcopanTheme.typography.label, color = c.inkStrong)
        val shape = EcopanShapes.control
        val borderColor = when {
            isError -> c.danger.fg
            focused -> c.crust
            else -> c.controlBorder
        }
        Box(
            modifier = Modifier
                .focusRing(focused, shape, c.focusRing)
                .background(c.card, shape)
                .border(BorderStroke(if (focused || isError) 2.dp else 1.dp, borderColor), shape),
        ) { field() }
        if (supportingText != null) {
            Text(
                supportingText,
                style = EcopanTheme.typography.bodySmall,
                color = if (isError) c.danger.fg else c.inkMuted,
            )
        }
    }
}

/**
 * A single- or multi-line text field: label above, 12dp radius, control border. Errors are shown
 * by a thicker danger border **and** [supportingText] -- supply the message, not just [isError].
 */
@Composable
fun EcopanTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    textStyle: TextStyle = EcopanTheme.typography.body,
) {
    val c = EcopanTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    FieldFrame(label, supportingText, isError, focused, enabled, modifier) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            readOnly = readOnly,
            singleLine = singleLine,
            textStyle = textStyle.copy(color = c.inkStrong),
            cursorBrush = SolidColor(c.crust),
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = interaction,
            decorationBox = { inner ->
                Row(
                    modifier = Modifier
                        .defaultMinSize(minHeight = EcopanSpacing.field)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (leadingIcon != null) Icon(leadingIcon, null, tint = c.inkMuted, modifier = Modifier.size(20.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(placeholder, style = textStyle, color = c.inkSubtle)
                        }
                        inner()
                    }
                    if (trailingIcon != null) Icon(trailingIcon, null, tint = c.inkMuted, modifier = Modifier.size(20.dp))
                }
            },
        )
    }
}

/**
 * A select: the closed field shows [selected] (or [placeholder]); activating it opens a menu of
 * [options]. State is the caller's: pass the chosen option and update it in [onSelect].
 */
@Composable
fun <T> EcopanSelect(
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    optionLabel: (T) -> String,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
) {
    val c = EcopanTheme.colors
    var expanded by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    // The menu is a popup, so it sizes itself to its content: left alone it came out narrower than
    // the field it opened from. Pin it to the field's measured width.
    var fieldWidthPx by remember { mutableIntStateOf(0) }
    val fieldWidth = with(LocalDensity.current) { fieldWidthPx.toDp() }
    FieldFrame(label, supportingText, isError, focused || expanded, enabled, modifier) {
        Box(Modifier.onSizeChanged { fieldWidthPx = it.width }) {
            Row(
                modifier = Modifier
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        enabled = enabled,
                        role = Role.DropdownList,
                    ) { expanded = true }
                    .defaultMinSize(minHeight = EcopanSpacing.field)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selected?.let(optionLabel) ?: placeholder.orEmpty(),
                    style = EcopanTheme.typography.body,
                    color = if (selected != null) c.inkStrong else c.inkSubtle,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    EcopanIcons.ExpandMore,
                    contentDescription = stringResource(Res.string.select_open_cd),
                    tint = c.inkMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = c.card,
                modifier = if (fieldWidthPx > 0) Modifier.width(fieldWidth) else Modifier,
            ) {
                for (option in options) {
                    DropdownMenuItem(
                        text = { Text(optionLabel(option), style = EcopanTheme.typography.body, color = c.inkBody) },
                        onClick = {
                            expanded = false
                            onSelect(option)
                        },
                        modifier = Modifier.defaultMinSize(minHeight = EcopanSpacing.controlMin),
                    )
                }
            }
        }
    }
}
