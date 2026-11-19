package com.f776.ecopan.data.example

import com.f776.ecopan.domain.example.Note
import com.f776.ecopan.domain.example.NoteRepository
import com.f776.unigrid.core.database.dao.NoteDao
import com.f776.unigrid.core.database.entity.NoteEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)
internal class NoteRepositoryImpl(private val noteDao: NoteDao) : NoteRepository {

    override fun observeNotes(): Flow<List<Note>> =
        noteDao.getAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun addNote(title: String, body: String) {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        noteDao.upsert(
            NoteEntity(
                id = Uuid.random(),
                title = title,
                body = body,
                createdAt = today,
                updatedAt = today,
            )
        )
    }

    override suspend fun deleteNote(id: Uuid) {
        noteDao.delete(
            NoteEntity(
                id = id,
                title = "",
                body = "",
                createdAt = today(),
                updatedAt = today(),
            )
        )
    }

    private fun today() = Clock.System.todayIn(TimeZone.currentSystemDefault())

    private fun NoteEntity.toDomain() = Note(
        id = id,
        title = title,
        body = body,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
