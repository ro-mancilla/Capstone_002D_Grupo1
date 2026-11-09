package com.f776.ecopan.data.sync.unittest

import androidx.room.Room
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.f776.ecopan.domain.sync.outboxRow
import com.f776.ecopan.domain.sync.SyncEntityType
import com.f776.ecopan.domain.sync.SyncOpType
import com.f776.unigrid.core.database.AppDatabase
import com.f776.unigrid.core.database.entity.NoteEntity
import com.f776.unigrid.core.database.entity.OutboxState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/**
 * `domainWriteAndOutboxEnqueueAreAtomic` (03-sync.md §2.1/§9 T8, plan 03 work item 3 acceptance
 * criterion): inserting an [outboxRow] is a plain DAO insert with no transaction or
 * dispatcher hop of its own (§2.1), so it must be safe to do inside the *same* Room transaction
 * as a domain write. This uses a real in-memory Room [AppDatabase] (JVM `BundledSQLiteDriver`, the
 * pattern established by `core/database`'s `OutboxSchemaTest`) and the KMP-compatible transaction
 * primitive `db.useWriterConnection { transactor -> transactor.immediateTransaction { ... } }`
 * (verified against Room 2.8.4's `androidx.room.TransactorKt`/`RoomDatabaseKt` via context7 --
 * *not* the Android-only `androidx.room.withTransaction` ktx extension).
 *
 * [NoteEntity]/`NoteDao` stand in for "a domain write" -- this template's reference domain
 * (CLAUDE.md), used here purely as a generic stand-in so this test stays domain-agnostic.
 */
class OutboxAtomicityTest {

    private val database = Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .build()

    @AfterTest
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `domainWriteAndOutboxEnqueueAreAtomic -- an aborted transaction persists neither row`() = runTest {
        val noteId = Uuid.random()
        val mutationId = Uuid.random()

        assertFailsWith<IllegalStateException> {
            database.useWriterConnection { transactor ->
                transactor.immediateTransaction {
                    database.noteDao().upsert(note(noteId))
                    database.outboxDao().insert(
                        outboxRow(mutationId, SyncEntityType.SALE, SyncOpType.CREATE, "{}"),
                    )
                    error("simulated crash between the domain write and commit")
                }
            }
        }

        assertEquals(emptyList(), database.noteDao().getAll().first())
        assertNull(database.outboxDao().stateOf(mutationId))
    }

    @Test
    fun `domainWriteAndOutboxEnqueueAreAtomic -- a committed transaction persists both rows`() = runTest {
        val noteId = Uuid.random()
        val mutationId = Uuid.random()

        database.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                database.noteDao().upsert(note(noteId))
                database.outboxDao().insert(
                        outboxRow(mutationId, SyncEntityType.SALE, SyncOpType.CREATE, "{}"),
                    )
            }
        }

        assertEquals(listOf(noteId), database.noteDao().getAll().first().map { it.id })
        assertEquals(OutboxState.PENDING, database.outboxDao().stateOf(mutationId))
    }

    private fun note(id: Uuid) = NoteEntity(
        id = id,
        title = "title",
        body = "body",
        createdAt = LocalDate(2026, 1, 1),
        updatedAt = LocalDate(2026, 1, 1),
    )
}
