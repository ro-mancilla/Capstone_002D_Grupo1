package com.f776.unigrid.core.database.converter

import androidx.room.TypeConverter
import com.f776.ecopan.core.domain.auth.Role

internal class RoleConverter {
    @TypeConverter
    fun fromRole(role: Role): String = role.name

    @TypeConverter
    fun toRole(value: String): Role = Role.valueOf(value)
}
