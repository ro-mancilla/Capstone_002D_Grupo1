package com.f776.unigrid.core.database.converter

import androidx.room.TypeConverter
import kotlinx.datetime.LocalDate

internal class LocalDateConverter {
    @TypeConverter
    fun fromDate(value: String?): LocalDate? {
        return value?.let { LocalDate.parse(it) }
    }

    @TypeConverter
    fun dateToString(date: LocalDate?): String? {
        return date?.toString()
    }
}
