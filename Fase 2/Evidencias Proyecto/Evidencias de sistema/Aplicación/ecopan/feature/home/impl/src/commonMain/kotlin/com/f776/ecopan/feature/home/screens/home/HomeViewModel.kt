@file:OptIn(ExperimentalUuidApi::class, ExperimentalCoroutinesApi::class)

package com.f776.ecopan.feature.home.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.f776.ecopan.domain.example.Note
import com.f776.ecopan.domain.example.NoteRepository
import com.f776.ecopan.domain.reminder.Reminder
import com.f776.ecopan.domain.reminder.ReminderScheduler
import com.f776.ecopan.domain.reminder.permission.PermissionResult
import com.f776.ecopan.domain.reminder.permission.ReminderPermissionManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi

/**
 * Reference view model for the Notes example feature: CRUD over [NoteRepository] plus an
 * optional one-shot [ReminderScheduler] reminder per note, gated by [ReminderPermissionManager].
 */
internal class HomeViewModel(
    private val noteRepository: NoteRepository,
    private val reminderScheduler: ReminderScheduler,
    private val reminderPermissionManager: ReminderPermissionManager,
) : ViewModel() {

    private val _eventChannel = Channel<HomeEvent>()
    val eventChannel = _eventChannel.receiveAsFlow()

    val notes: StateFlow<List<Note>> = noteRepository.observeNotes()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(2.seconds),
            initialValue = emptyList()
        )

    fun onAction(action: HomeAction) {
        when (action) {
            is HomeAction.OnAddNote -> viewModelScope.launch {
                noteRepository.addNote(title = action.title, body = action.body)
            }

            is HomeAction.OnDeleteNote -> viewModelScope.launch {
                reminderScheduler.cancel(reminderId = action.id.toString())
                noteRepository.deleteNote(action.id)
            }

            is HomeAction.OnRemindNote -> viewModelScope.launch {
                val result = reminderPermissionManager.requestPermissions()
                if (result != PermissionResult.GRANTED) {
                    _eventChannel.send(HomeEvent.OnPermissionDenied)
                    return@launch
                }

                val note = notes.value.firstOrNull { it.id == action.id } ?: return@launch
                reminderScheduler.schedule(
                    Reminder(
                        id = note.id.toString(),
                        triggerAt = action.triggerAt,
                        title = note.title,
                        message = note.body,
                    )
                )
                _eventChannel.send(HomeEvent.OnReminderScheduled(note.title))
            }
        }
    }
}
