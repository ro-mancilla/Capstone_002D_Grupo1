package com.f776.ecopan.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import java.io.File

fun createDataStore(): DataStore<Preferences> {
    val preferencesFile = File(ecopanAppDataDir(), APP_PREFERENCE_NAME)
    adoptLegacyWorkingDirectoryStore(preferencesFile)
    return createDataStore(
        producePath = { preferencesFile.absolutePath }
    )
}

/**
 * Carries a pre-[ecopanAppDataDir] store over to its permanent home, once.
 *
 * The old path was the JVM working directory, so an existing install's device session sits in
 * whichever folder the till was last launched from. Without this, the fix to
 * [createDataStore] would *itself* log every desktop install out: it would start reading a file
 * that does not exist yet, find no `device_session_refresh_token`, and land on the same
 * "Requiere atención" pill this change exists to remove.
 *
 * Only moves into an empty slot, and only from the current working directory — the one location
 * the old code could have written. If the destination already holds a store it is the real one and
 * is left alone; a stale working-directory copy is then just a file, not a second identity.
 */
private fun adoptLegacyWorkingDirectoryStore(destination: File) {
    if (destination.exists()) return
    val legacy = File(System.getProperty("user.dir"), APP_PREFERENCE_NAME)
    if (!legacy.isFile) return
    runCatching { legacy.copyTo(destination, overwrite = false) }
        .onSuccess { legacy.delete() }
}
