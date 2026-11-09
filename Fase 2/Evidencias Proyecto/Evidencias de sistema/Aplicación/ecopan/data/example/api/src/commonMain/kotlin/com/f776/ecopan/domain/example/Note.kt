package com.f776.ecopan.domain.example

import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

data class Note(
    val id: Uuid,
    val title: String,
    val body: String,
    val createdAt: LocalDate,
    val updatedAt: LocalDate,
)
