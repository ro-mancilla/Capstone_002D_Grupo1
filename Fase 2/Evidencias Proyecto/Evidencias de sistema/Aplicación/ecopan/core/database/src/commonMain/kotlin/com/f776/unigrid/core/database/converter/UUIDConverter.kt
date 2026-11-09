package com.f776.unigrid.core.database.converter

import androidx.room.TypeConverter
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

internal class UUIDConverter {
    @TypeConverter
    fun fromUuid(value: String?): Uuid? {
        return value?.let { Uuid.parse(it) }
    }

    @TypeConverter
    fun uuidToString(uuid: Uuid?): String? {
        return uuid?.toString()
    }
}