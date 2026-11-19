package com.f776.unigrid.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.AndroidSQLiteDriver

actual class DatabaseFactory(
    private val context: Context
) {
    actual fun create(): RoomDatabase.Builder<AppDatabase> {
        val appContext = context.applicationContext
        val dbFile = appContext.getDatabasePath(AppDatabase.DATABASE_NAME)

        return Room.databaseBuilder(
            context = appContext,
            name = dbFile.absolutePath,
            klass = AppDatabase::class.java,
        ).fallbackToDestructiveMigration(false)
            .addMigrations(MIGRATION_1_2)
            .setDriver(AndroidSQLiteDriver())
    }
}
