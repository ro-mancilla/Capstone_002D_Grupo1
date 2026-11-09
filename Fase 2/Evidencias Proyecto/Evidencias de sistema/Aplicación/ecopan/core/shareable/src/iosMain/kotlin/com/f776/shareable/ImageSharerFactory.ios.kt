@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.f776.shareable

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ecopan.core.shareable.generated.resources.Res
import ecopan.core.shareable.generated.resources.share_schedule_message
import org.jetbrains.compose.resources.getString
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.UIKit.popoverPresentationController

internal actual class ImageSharerFactory(private val ioDispatcher: CoroutineDispatcher): ImageSharer {
    actual fun getImageSharer(): ImageSharer = this

    override suspend fun shareImage(
        byteArray: ByteArray,
        filename: String,
        extension: String,
        title: String
    ): String? = withContext(ioDispatcher) {
        try {
            // Get temporary directory
            val tmpDir = NSTemporaryDirectory()
            val filePath = "$tmpDir$filename.$extension"

            // Write image data to file
            val data = byteArray.toNSData()
            data.writeToFile(filePath, atomically = true)

            // Create file URL
            val fileUrl = NSURL.fileURLWithPath(filePath)

            // Get share message
            val shareMessage = getString(Res.string.share_schedule_message, title)

            // Switch to main thread for UI operations
            withContext(Dispatchers.Main) {
                // Create activity items (message and image)
                val activityItems = listOf(shareMessage, fileUrl)

                // Create UIActivityViewController
                val activityController = UIActivityViewController(
                    activityItems = activityItems,
                    applicationActivities = null
                )

                // Get root view controller
                val window = UIApplication.sharedApplication.windows.firstOrNull() as? UIWindow
                val rootViewController = window?.rootViewController

                // Configure for iPad (popover)
                activityController.popoverPresentationController?.sourceView = window

                // Present share sheet
                rootViewController?.presentViewController(
                    activityController,
                    animated = true,
                    completion = null
                )
            }

            filePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

// Extension function to convert ByteArray to NSData
private fun ByteArray.toNSData(): NSData {
    return this.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = this.size.toULong())
    }
}
