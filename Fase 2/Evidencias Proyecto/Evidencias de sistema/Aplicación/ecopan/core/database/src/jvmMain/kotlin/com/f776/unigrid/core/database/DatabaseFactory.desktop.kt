package com.f776.unigrid.core.database

import androidx.room.Room
import androidx.room.RoomDatabase
import com.f776.ecopan.core.datastore.ecopanAppDataDir
import java.io.File

actual class DatabaseFactory {
    actual fun create(): RoomDatabase.Builder<AppDatabase> {
        val dbFile = File(ecopanAppDataDir(), AppDatabase.DATABASE_NAME)
        return Room.databaseBuilder<AppDatabase>(dbFile.absolutePath)
    }
}
