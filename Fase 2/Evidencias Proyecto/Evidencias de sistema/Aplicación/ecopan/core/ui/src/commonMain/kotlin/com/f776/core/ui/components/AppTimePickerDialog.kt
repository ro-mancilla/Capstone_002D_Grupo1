@file:OptIn(ExperimentalMaterial3Api::class)

package com.f776.core.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.window.core.layout.WindowSizeClass
import com.f776.core.ui.locale.LocalClockFormat
import kotlinx.datetime.LocalTime
import ecopan.core.ui.generated.resources.Res
import ecopan.core.ui.generated.resources.accept
import ecopan.core.ui.generated.resources.cancel
import ecopan.core.ui.generated.resources.switch_to_dial_cd
import ecopan.core.ui.generated.resources.switch_to_keyboard_input_cd
import org.jetbrains.compose.resources.stringResource

/**
 * The app's single time picker dialog.
 *
 * It owns its [androidx.compose.material3.TimePickerState] on purpose. Callers used to hoist that
 * state next to the screen and seed it with a fixed hour, which silently discarded the value being
 * edited; taking [initialTime] instead makes seeding the only way to use it. Compose the dialog
 * inside the branch that shows it so a fresh state is built from the current value on every open.
 *
 * [is24Hour] defaults to the user's own clock convention rather than to 24-hour, so someone on a
 * 12-hour locale gets an AM/PM dial matching the times the rest of the app shows them.
 */
@Composable
fun AppTimePickerDialog(
    initialTime: LocalTime?,
    title: String,
    onDismissRequest: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
    is24Hour: Boolean = LocalClockFormat.current.uses24HourClock()
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialTime?.hour ?: DEFAULT_HOUR,
        initialMinute = initialTime?.minute ?: 0,
        is24Hour = is24Hour
    )

    // The dial needs vertical room it does not have on a landscape phone, so short windows open
    // straight into keyboard entry. Everywhere else the dial's own layout type adapts on its own.
    val hasRoomForDial = currentWindowAdaptiveInfo().windowSizeClass
        .isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)
    var showDial by remember { mutableStateOf(hasRoomForDial) }

    TimePickerDialog(
        title = { Text(text = title) },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = { onConfirm(LocalTime(timePickerState.hour, timePickerState.minute)) }
            ) {
                Text(stringResource(Res.string.accept))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(Res.string.cancel))
            }
        },
        modeToggleButton = {
            IconButton(onClick = { showDial = !showDial }) {
                Icon(
                    imageVector = if (showDial) Icons.Default.Keyboard else Icons.Default.AccessTime,
                    contentDescription = if (showDial) {
                        stringResource(Res.string.switch_to_keyboard_input_cd)
                    } else {
                        stringResource(Res.string.switch_to_dial_cd)
                    }
                )
            }
        }
    ) {
        if (showDial) {
            TimePicker(state = timePickerState)
        } else {
            TimeInput(state = timePickerState)
        }
    }
}

private const val DEFAULT_HOUR = 8
