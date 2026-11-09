package com.f776.shareable

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import ecopan.core.shareable.generated.resources.Res
import ecopan.core.shareable.generated.resources.save_schedule
import org.jetbrains.compose.resources.getString
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FileOutputStream
import java.io.FilenameFilter

internal actual class ImageSharerFactory(private val ioDispatcher: CoroutineDispatcher) : ImageSharer {
    actual fun getImageSharer(): ImageSharer = this

    override suspend fun shareImage(
        byteArray: ByteArray,
        filename: String,
        extension: String,
        title: String
    ): String? = withContext(ioDispatcher) {
        try {
            val shareMessage = getString(Res.string.save_schedule)
            // Create native file dialog
            val fileDialog = FileDialog(null as Frame?, shareMessage, FileDialog.SAVE).apply {
                file = "$filename.$extension"

                // Add file filter for the specific extension
                filenameFilter = FilenameFilter { _, name ->
                    name.endsWith(".$extension", ignoreCase = true)
                }
            }

            // Show save dialog
            fileDialog.isVisible = true

            // Check if user selected a file (not cancelled)
            if (fileDialog.file != null) {
                var selectedFile = File(fileDialog.directory, fileDialog.file)

                // Ensure file has the correct extension
                if (!selectedFile.name.endsWith(".$extension", ignoreCase = true)) {
                    selectedFile = File(selectedFile.absolutePath + ".$extension")
                }

                // Write the image bytes to the selected file
                FileOutputStream(selectedFile).use { outputStream ->
                    outputStream.write(byteArray)
                    outputStream.flush()
                }

                // Optionally open the file location (if supported by the system)
                try {
                    if (Desktop.isDesktopSupported()) {
                        val desktop = Desktop.getDesktop()
                        if (desktop.isSupported(Desktop.Action.OPEN)) {
                            desktop.open(selectedFile.parentFile)
                        }
                    }
                } catch (e: Exception) {
                    // Ignore if we can't open the folder
                    e.printStackTrace()
                }

                selectedFile.absolutePath
            } else {
                // User cancelled the dialog
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}