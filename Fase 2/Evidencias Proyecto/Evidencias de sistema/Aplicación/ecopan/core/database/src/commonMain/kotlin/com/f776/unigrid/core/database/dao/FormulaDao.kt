package com.f776.unigrid.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.f776.unigrid.core.database.entity.FormulaEntity
import com.f776.unigrid.core.database.entity.FormulaLineEntity
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

@Dao
interface FormulaDao {
    @Upsert
    suspend fun upsertFormula(formula: FormulaEntity)

    @Query("DELETE FROM formula_lines WHERE formulaId = :formulaId")
    suspend fun deleteLines(formulaId: Uuid)

    @Upsert
    suspend fun insertLines(lines: List<FormulaLineEntity>)

    /** Replace-all wholesale (02 §1: "Lines replaced wholesale on formula PUT"), one transaction. */
    @Transaction
    suspend fun replaceFormula(formula: FormulaEntity, lines: List<FormulaLineEntity>) {
        upsertFormula(formula)
        deleteLines(formula.id)
        insertLines(lines)
    }

    @Query("SELECT * FROM formulas WHERE productId = :productId AND active = 1 LIMIT 1")
    fun observeByProduct(productId: Uuid): Flow<FormulaEntity?>

    // rowid = insertion order = the order the server sent the lines in (replaced wholesale per
    // formula). Without it SQLite picks the order, and the deduction list's order is the formula's.
    @Query("SELECT * FROM formula_lines WHERE formulaId = :formulaId ORDER BY rowid")
    fun observeLines(formulaId: Uuid): Flow<List<FormulaLineEntity>>

    @Query("DELETE FROM formulas")
    suspend fun clearFormulas()

    @Query("DELETE FROM formula_lines")
    suspend fun clearLines()
}
