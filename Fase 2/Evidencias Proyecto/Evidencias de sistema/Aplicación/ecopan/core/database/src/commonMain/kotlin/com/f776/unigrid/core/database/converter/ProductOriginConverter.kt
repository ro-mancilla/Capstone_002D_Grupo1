package com.f776.unigrid.core.database.converter

import androidx.room.TypeConverter
import com.f776.ecopan.core.domain.catalog.ProductOrigin

internal class ProductOriginConverter {
    @TypeConverter
    fun fromProductOrigin(origin: ProductOrigin): String = origin.name

    @TypeConverter
    fun toProductOrigin(value: String): ProductOrigin = ProductOrigin.valueOf(value)
}
