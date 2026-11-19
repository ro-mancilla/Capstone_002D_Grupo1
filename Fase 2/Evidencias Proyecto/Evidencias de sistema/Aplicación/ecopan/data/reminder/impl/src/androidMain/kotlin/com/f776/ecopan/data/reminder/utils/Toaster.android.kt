package com.f776.ecopan.data.reminder.utils

import android.content.Context
import android.widget.Toast

actual class Toaster(private val context: Context) {
    actual fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}