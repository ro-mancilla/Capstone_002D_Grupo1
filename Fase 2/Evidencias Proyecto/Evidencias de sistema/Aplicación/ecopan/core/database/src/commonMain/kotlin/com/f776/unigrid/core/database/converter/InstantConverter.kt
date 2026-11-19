package com.f776.unigrid.core.database.converter

import androidx.room.TypeConverter
import kotlin.time.Instant

/** Stored as epoch millis (01 §3). */
internal class InstantConverter {
    @TypeConverter
    fun fromEpochMillis(value: Long): Instant = Instant.fromEpochMilliseconds(value)

    @TypeConverter
    fun toEpochMillis(instant: Instant): Long = instant.toEpochMilliseconds()
}
