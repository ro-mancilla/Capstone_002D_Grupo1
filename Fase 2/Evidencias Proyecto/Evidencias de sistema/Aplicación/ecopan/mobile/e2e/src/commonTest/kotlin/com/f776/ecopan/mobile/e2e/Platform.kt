package com.f776.ecopan.mobile.e2e

/** Starts the app's real Koin graph with whatever the platform needs (an Android context, an isolated data directory). */
internal expect fun startAppKoin()

/** The throwaway stack's API as this platform reaches it. */
internal expect val e2eApi: String

/** Blocks the test thread; the app runs on its own threads meanwhile. */
internal expect fun sleep(millis: Long)

/** The runner's fault-control server (scripts/e2e/control.py), as this platform reaches the host. */
internal const val e2eControl = "http://localhost:18999"

/** The throwaway stack's sign-in throttle drains at this rate; a bucket takes about this long to empty (backend rate limit). */
internal const val THROTTLE_WINDOW_MS = 90_000L

/** Lets work queued on the main thread run while a test waits (the iOS main run loop does not spin on its own in a test); a no-op on Android. */
internal expect fun pumpMainThread()

/**
 * Whether the Settings screen can be opened in this test process. On iOS it cannot: the notification-permission row asks
 * `UNUserNotificationCenter`, which aborts the process when there is no app bundle (a test executable has none).
 */
internal expect val settingsAvailable: Boolean
