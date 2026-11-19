package com.f776.ecopan.core.datastore

import java.io.File

/**
 * The one place the desktop client's persistent state lives, per OS convention.
 *
 * This exists because it used to be *two* places. The Room database resolved this directory; the
 * preferences store used `System.getProperty("user.dir")` — the JVM's working directory — so the
 * two halves of a device's identity had different stability guarantees. The database is keyed to
 * the machine, the preferences file was keyed to *the folder the process happened to start in*.
 *
 * The failure that produced was quiet and looked like a server fault. A till launched via
 * `:jvm:app:run` from the repo root and one launched via `hotRun` from `jvm/app/` shared a
 * database but not a token store, so the second one read its tenant, branch and catalog from Room
 * and rendered as fully enrolled — while holding no `device_session_refresh_token`. Every sync
 * request then went out unauthenticated, the server answered 401, `SyncAuthProvider.refresh()`
 * found no refresh token and returned `false` without making a call, and both workers mapped that
 * to `AuthRequired` → the permanent "Requiere atención" pill. The give-away in the server log was
 * a stream of 401s with **no `POST /auth/refresh` between them**: the client was not failing to
 * refresh, it was never trying.
 *
 * `sync_device_id` lives in the same store, so each working directory also minted its own device
 * identity — one install, several "devices", from the operator's point of view.
 *
 * Anything that persists desktop state resolves it here. Do not re-derive it.
 */
fun ecopanAppDataDir(): File {
    val os = System.getProperty("os.name").orEmpty().lowercase()
    val userHome = System.getProperty("user.home")
    val dir = when {
        os.contains("win") -> File(System.getenv("APPDATA") ?: userHome, "Ecopan")
        os.contains("mac") -> File(userHome, "Library/Application Support/Ecopan")
        else -> File(userHome, ".local/share/Ecopan")
    }
    if (!dir.exists()) dir.mkdirs()
    return dir
}
