package com.f776.ecopan.data.reminder.utils

import co.touchlab.kermit.Logger

actual class Toaster {
    actual fun showToast(message: String) {
        Logger.i { message }
    }
}