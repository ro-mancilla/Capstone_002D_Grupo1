package com.f776.ecopan.data.reminder.notification

import co.touchlab.kermit.Logger

/**
 * Shells out to whichever notification mechanism the host desktop provides.
 */
internal object DesktopNotifications {

    fun show(title: String, message: String): Boolean = when {
        isLinux() -> notifyLinux(title, message)
        isWindows() -> notifyWindows(title, message)
        isMac() -> notifyMac(title, message)
        else -> {
            Logger.e {
                "Unsupported platform for native notifications: ${System.getProperty("os.name")}"
            }
            false
        }
    }

    private fun isLinux(): Boolean = System.getProperty("os.name").lowercase().contains("linux")
    private fun isWindows(): Boolean = System.getProperty("os.name").lowercase().contains("win")
    private fun isMac(): Boolean = System.getProperty("os.name").lowercase().contains("mac")

    private fun notifyLinux(title: String, message: String): Boolean = runCatching {
        val process = ProcessBuilder(
            "notify-send",
            "-a", "Ecopan",
            "-i", "appointment-new",
            title,
            message
        ).start()
        process.waitFor()
        process.exitValue() == 0
    }.getOrElse {
        Logger.e(it) { "Linux notification failed: ${it.message}" }
        false
    }

    private fun notifyWindows(title: String, message: String): Boolean = runCatching {
        val script = """
            [Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime] | Out-Null
            [Windows.Data.Xml.Dom.XmlDocument, Windows.Data.Xml.Dom.XmlDocument, ContentType = WindowsRuntime] | Out-Null
            ${'$'}template = [Windows.UI.Notifications.ToastNotificationManager]::GetTemplateContent([Windows.UI.Notifications.ToastTemplateType]::ToastText02)
            ${'$'}textNodes = ${'$'}template.GetElementsByTagName("text")
            ${'$'}textNodes.Item(0).AppendChild(${'$'}template.CreateTextNode("$title")) | Out-Null
            ${'$'}textNodes.Item(1).AppendChild(${'$'}template.CreateTextNode("$message")) | Out-Null
            ${'$'}toast = [Windows.UI.Notifications.ToastNotification]::new(${'$'}template)
            [Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier("Ecopan").Show(${'$'}toast)
        """.trimIndent()

        val process = ProcessBuilder(
            "powershell.exe",
            "-NoProfile",
            "-ExecutionPolicy", "Bypass",
            "-Command", script
        ).start()
        process.waitFor()
        process.exitValue() == 0
    }.getOrElse {
        Logger.e(it) { "Windows notification failed: ${it.message}" }
        false
    }

    private fun notifyMac(title: String, message: String): Boolean = runCatching {
        val process = ProcessBuilder(
            "osascript",
            "-e", "display notification \"$message\" with title \"$title\""
        ).start()
        process.waitFor()
        process.exitValue() == 0
    }.getOrElse {
        Logger.e(it) { "macOS notification failed: ${it.message}" }
        false
    }
}
