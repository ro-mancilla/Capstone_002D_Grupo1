package com.f776.ecopan.mobile.e2e

import androidx.test.core.app.ApplicationProvider
import com.f776.ecopan.di.initKoin
import org.koin.android.ext.koin.androidContext

// The test APK runs the app's code in its own process; the emulator reaches the stack through `adb reverse`.
internal actual fun startAppKoin() {
    initKoin { androidContext(ApplicationProvider.getApplicationContext()) }
}

internal actual val e2eApi: String = "http://localhost:8080"
internal actual fun sleep(millis: Long) = Thread.sleep(millis)

internal actual fun pumpMainThread() = Unit

internal actual val settingsAvailable: Boolean = true
