package com.f776.unigrid.core.database.converter

import androidx.room.TypeConverter
import com.f776.ecopan.core.domain.tenant.EntitlementModule

internal class EntitlementModuleSetConverter {
    @TypeConverter
    fun fromSet(modules: Set<EntitlementModule>): String = modules.joinToString(",") { it.name }

    @TypeConverter
    fun toSet(value: String): Set<EntitlementModule> =
        if (value.isEmpty()) emptySet() else value.split(",").map { EntitlementModule.valueOf(it) }.toSet()
}
