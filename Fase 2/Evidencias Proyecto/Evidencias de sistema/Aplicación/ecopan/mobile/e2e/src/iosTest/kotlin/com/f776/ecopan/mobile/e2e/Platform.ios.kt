package com.f776.ecopan.mobile.e2e

import com.f776.ecopan.di.initKoin
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDate
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSDefaultRunLoopMode
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSRunLoop
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.timeIntervalSinceNow
import platform.Foundation.runMode

@OptIn(ExperimentalForeignApi::class)
internal actual fun startAppKoin() {
    // A test process spawned by simctl has no app container yet: make the Documents directory the app's database and
    // preferences live in.
    val documents = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).first() as String
    // The simulator keeps that directory between runs, and with it the previous run's database and session (tokens of a
    // stack that no longer exists): every run starts from an empty phone.
    NSFileManager.defaultManager.removeItemAtPath(documents, error = null)
    NSFileManager.defaultManager.createDirectoryAtPath(documents, withIntermediateDirectories = true, attributes = null, error = null)
    initKoin()
}

internal actual val e2eApi: String =
    (NSProcessInfo.processInfo.environment["ECOPAN_E2E_API"] as? String) ?: "http://localhost:18080"
internal actual fun sleep(millis: Long) {
    val until = NSDate.dateWithTimeIntervalSinceNow(millis / 1000.0)
    while (until.timeIntervalSinceNow > 0) pumpMainThread()
}

internal actual fun pumpMainThread() {
    NSRunLoop.currentRunLoop.runMode(NSDefaultRunLoopMode, beforeDate = NSDate.dateWithTimeIntervalSinceNow(0.01))
}

internal actual val settingsAvailable: Boolean = false
