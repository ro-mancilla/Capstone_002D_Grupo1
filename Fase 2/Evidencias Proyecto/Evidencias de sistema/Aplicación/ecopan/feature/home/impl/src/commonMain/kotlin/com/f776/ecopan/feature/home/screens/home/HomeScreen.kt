@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)

package com.f776.ecopan.feature.home.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.f776.core.ui.appstate.LocalSnackbarAppState
import com.f776.core.ui.components.ActionSheetItem
import com.f776.core.ui.components.ActionsBottomSheet
import com.f776.core.ui.components.AppTimePickerDialog
import com.f776.core.designsystem.component.HintMessage
import com.f776.core.ui.components.ObserveAsEvents
import com.f776.core.ui.preview.LocalizedPreview
import com.f776.core.designsystem.theme.EcopanTheme
import com.f776.ecopan.domain.example.Note
import ecopan.feature.home.impl.generated.resources.Res
import ecopan.feature.home.impl.generated.resources.add_cd
import ecopan.feature.home.impl.generated.resources.add_note_title
import ecopan.feature.home.impl.generated.resources.cancel_button
import ecopan.feature.home.impl.generated.resources.create_your_first_note_subtitle
import ecopan.feature.home.impl.generated.resources.delete_action
import ecopan.feature.home.impl.generated.resources.no_notes_found_title
import ecopan.feature.home.impl.generated.resources.note_body_label
import ecopan.feature.home.impl.generated.resources.note_title_label
import ecopan.feature.home.impl.generated.resources.notes_title
import ecopan.feature.home.impl.generated.resources.permission_denied_message
import ecopan.feature.home.impl.generated.resources.pick_reminder_time_title
import ecopan.feature.home.impl.generated.resources.remind_me_action
import ecopan.feature.home.impl.generated.resources.reminder_scheduled_message
import ecopan.feature.home.impl.generated.resources.save_button
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Composable
internal fun HomeScreenRoot(
    modifier: Modifier = Modifier,
    homeViewModel: HomeViewModel = koinViewModel()
) {
    val notes by homeViewModel.notes.collectAsStateWithLifecycle()
    val snackbarAppState = LocalSnackbarAppState.current

    val reminderScheduledTemplate = stringResource(Res.string.reminder_scheduled_message, $$"%1$s")
    val permissionDeniedMessage = stringResource(Res.string.permission_denied_message)

    ObserveAsEvents(homeViewModel.eventChannel) {
        when (it) {
            is HomeEvent.OnReminderScheduled -> {
                snackbarAppState.showSnackbar(reminderScheduledTemplate.replace($$"%1$s", it.noteTitle))
            }

            HomeEvent.OnPermissionDenied -> {
                snackbarAppState.showSnackbar(permissionDeniedMessage)
            }
        }
    }

    HomeScreen(
        modifier = modifier,
        notes = notes,
        onAction = homeViewModel::onAction
    )
}

@OptIn(ExperimentalUuidApi::class)
@Composable
private fun HomeScreen(
    modifier: Modifier = Modifier,
    notes: List<Note>,
    onAction: (HomeAction) -> Unit,
) {
    var showAddNoteDialog by rememberSaveable { mutableStateOf(false) }
    var actionNoteId by rememberSaveable { mutableStateOf<String?>(null) }
    var showActionsSheet by rememberSaveable { mutableStateOf(false) }
    var showTimePickerFor by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(stringResource(Res.string.notes_title), fontWeight = FontWeight.SemiBold)
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddNoteDialog = true }) {
                Icon(imageVector = Icons.Default.Add, contentDescription = stringResource(Res.string.add_cd))
            }
        }
    ) { innerPadding ->
        if (notes.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                HintMessage(
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.NoteAlt,
                    title = stringResource(Res.string.no_notes_found_title),
                    subtitle = stringResource(Res.string.create_your_first_note_subtitle)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 88.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items = notes, key = { it.id.toString() }) { note ->
                    NoteCard(
                        note = note,
                        onLongClick = {
                            actionNoteId = note.id.toString()
                            showActionsSheet = true
                        }
                    )
                }
            }
        }
    }

    val actionNote = notes.firstOrNull { it.id.toString() == actionNoteId }

    if (showActionsSheet && actionNote != null) {
        ActionsBottomSheet(
            title = actionNote.title,
            onDismissRequest = { showActionsSheet = false }
        ) { dismissThen ->
            ActionSheetItem(
                label = stringResource(Res.string.remind_me_action),
                icon = Icons.Default.Notifications,
                onClick = { dismissThen { showTimePickerFor = actionNote.id.toString() } }
            )
            ActionSheetItem(
                label = stringResource(Res.string.delete_action),
                icon = Icons.Default.Delete,
                onClick = { dismissThen { onAction(HomeAction.OnDeleteNote(actionNote.id)) } }
            )
        }
    }

    val timePickerNote = notes.firstOrNull { it.id.toString() == showTimePickerFor }
    if (timePickerNote != null) {
        AppTimePickerDialog(
            initialTime = defaultReminderTime(),
            title = stringResource(Res.string.pick_reminder_time_title),
            onDismissRequest = { showTimePickerFor = null },
            onConfirm = { time ->
                showTimePickerFor = null
                onAction(HomeAction.OnRemindNote(timePickerNote.id, time.toTriggerInstant()))
            }
        )
    }

    if (showAddNoteDialog) {
        AddNoteDialog(
            onDismissRequest = { showAddNoteDialog = false },
            onConfirm = { title, body ->
                showAddNoteDialog = false
                onAction(HomeAction.OnAddNote(title, body))
            }
        )
    }
}

@Composable
private fun NoteCard(note: Note, onLongClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onLongClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = note.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (note.body.isNotBlank()) {
                Text(
                    text = note.body,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3
                )
            }
        }
    }
}

@Composable
private fun AddNoteDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (title: String, body: String) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(Res.string.add_note_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(Res.string.note_title_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text(stringResource(Res.string.note_body_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title, body) },
                enabled = title.isNotBlank()
            ) {
                Text(stringResource(Res.string.save_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(Res.string.cancel_button))
            }
        }
    )
}

/** Defaults the reminder picker to roughly ten minutes from now. */
@OptIn(ExperimentalTime::class)
private fun defaultReminderTime(): LocalTime {
    val zone = TimeZone.currentSystemDefault()
    val inTenMinutes = Clock.System.now().plus(10, DateTimeUnit.MINUTE, zone)
    return inTenMinutes.toLocalDateTime(zone).time
}

/**
 * Resolves a picked wall-clock [LocalTime] to a concrete trigger [kotlin.time.Instant]: today if
 * that time is still ahead of now, tomorrow otherwise — picking "8:00" at 9pm means tomorrow
 * morning, not an alarm that fires the moment it's confirmed.
 */
@OptIn(ExperimentalTime::class)
private fun LocalTime.toTriggerInstant(): kotlin.time.Instant {
    val zone = TimeZone.currentSystemDefault()
    val now = Clock.System.now()
    val today = now.toLocalDateTime(zone).date
    val candidate = LocalDateTime(today, this).toInstant(zone)
    return if (candidate > now) candidate else candidate.plus(1, DateTimeUnit.DAY, zone)
}

@LocalizedPreview
@Composable
private fun HomeScreenPreview() {
    EcopanTheme {
        Surface {
            HomeScreen(
                notes = emptyList(),
                onAction = {}
            )
        }
    }
}
