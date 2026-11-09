package com.f776.ecopan.domain.example

import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

/**
 * Reference repository showing the api/impl split this template uses everywhere: `impl` wraps a
 * `core:database` DAO and maps entities to domain models, `api` stays free of any Room import.
 */
interface NoteRepository {
    fun observeNotes(): Flow<List<Note>>
    suspend fun addNote(title: String, body: String)
    suspend fun deleteNote(id: Uuid)
}
