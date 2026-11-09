package com.f776.ecopan.feature.home.unittest.fake

import com.f776.ecopan.domain.example.Note
import com.f776.ecopan.domain.example.NoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.LocalDate
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class FakeNoteRepository : NoteRepository {
    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes = _notes.asStateFlow()

    override fun observeNotes() = notes

    override suspend fun addNote(title: String, body: String) {
        val today = LocalDate(2026, 1, 1)
        _notes.value += Note(
            id = Uuid.random(),
            title = title,
            body = body,
            createdAt = today,
            updatedAt = today,
        )
    }

    override suspend fun deleteNote(id: Uuid) {
        _notes.value = _notes.value.filterNot { it.id == id }
    }

    fun setNotes(notes: List<Note>) {
        _notes.value = notes
    }
}
