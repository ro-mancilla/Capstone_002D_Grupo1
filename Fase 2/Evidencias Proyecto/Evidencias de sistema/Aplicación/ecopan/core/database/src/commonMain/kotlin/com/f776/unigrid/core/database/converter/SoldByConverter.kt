package com.f776.unigrid.core.database.converter

import androidx.room.TypeConverter
import com.f776.ecopan.core.domain.catalog.SoldBy

internal class SoldByConverter {
    @TypeConverter
    fun fromSoldBy(soldBy: SoldBy): String = soldBy.name

    @TypeConverter
    fun toSoldBy(value: String): SoldBy = SoldBy.valueOf(value)
}
