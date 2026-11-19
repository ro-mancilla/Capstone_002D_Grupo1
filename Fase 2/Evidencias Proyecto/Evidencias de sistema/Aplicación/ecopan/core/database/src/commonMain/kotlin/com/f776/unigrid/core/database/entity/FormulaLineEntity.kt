package com.f776.unigrid.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.uuid.Uuid

/**
 * A formula's ingredient lines (02 §1 `formula_lines`). Lines carry no [serverSeq] of their own
 * — they ride along in the owning formula's pull payload and are replaced wholesale together
 * (02 §1: "Lines replaced wholesale on formula PUT").
 */
@Entity(
    tableName = "formula_lines",
    indices = [Index(value = ["formulaId", "ingredientId"], unique = true), Index(value = ["formulaId"])],
)
data class FormulaLineEntity(
    @PrimaryKey val id: Uuid,
    val tenantId: Uuid,
    val formulaId: Uuid,
    val ingredientId: Uuid,
    val basisPoints: Int,
)
