package com.f776.unigrid.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

/**
 * Placeholder entity demonstrating the Room setup: swap this out for your app's real tables.
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: Uuid,
    val title: String,
    val body: String,
    val createdAt: LocalDate,
    val updatedAt: LocalDate,
)
