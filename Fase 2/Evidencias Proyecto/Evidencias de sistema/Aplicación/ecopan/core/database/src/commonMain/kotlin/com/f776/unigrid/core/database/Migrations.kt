package com.f776.unigrid.core.database

import androidx.room.DeleteColumn
import androidx.room.DeleteTable
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Worked example of a manual migration: [NoteEntity][com.f776.unigrid.core.database.entity.NoteEntity]
 * grew an `updatedAt` column between v1 and v2. Add one of these per schema change, in order, to
 * `DatabaseFactory`'s `.addMigrations(...)` call.
 *
 * The v2->v3 and v3->v4 bumps (tenant/catalog config cache tables, 01 §2) are purely additive —
 * new tables, no renamed/altered existing columns — so they use `@Database(autoMigrations = [...])`
 * on [AppDatabase] instead: Room diffs the exported schema JSON and generates the migration
 * itself, which is strictly safer than hand-copied SQL for this class of change.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE notes ADD COLUMN updatedAt TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * v9 -> v10: the day-old bread feature was removed, so its policy cache table and the two
 * per-line flags on `sale_lines` go. Room cannot infer a deletion from the schema diff (it could
 * equally be a rename), so this spec names them; the rest of the migration is still generated.
 */
@DeleteTable(tableName = "day_old_policies")
@DeleteColumn(tableName = "sale_lines", columnName = "dayOld")
@DeleteColumn(tableName = "sale_lines", columnName = "dayOldDiscountBpApplied")
class DropDayOldSpec : AutoMigrationSpec
