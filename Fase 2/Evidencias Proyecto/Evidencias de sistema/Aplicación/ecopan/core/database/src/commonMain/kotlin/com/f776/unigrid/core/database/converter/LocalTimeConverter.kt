package com.f776.unigrid.core.database.converter

import androidx.room.TypeConverter
import kotlinx.datetime.LocalTime

internal class LocalTimeConverter {
    @TypeConverter
    fun fromTime(value: String?): LocalTime? {
        return value?.let { LocalTime.parse(it) }
    }

    @TypeConverter
    fun timeToString(time: LocalTime?): String? {
        return time?.toString()
    }
}