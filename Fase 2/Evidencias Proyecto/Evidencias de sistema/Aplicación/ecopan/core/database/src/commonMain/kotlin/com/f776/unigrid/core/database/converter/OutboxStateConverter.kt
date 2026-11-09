package com.f776.unigrid.core.database.converter

import androidx.room.TypeConverter
import com.f776.unigrid.core.database.entity.OutboxState

internal class OutboxStateConverter {
    @TypeConverter
    fun fromState(state: OutboxState): String = state.name

    @TypeConverter
    fun toState(value: String): OutboxState = OutboxState.valueOf(value)
}
