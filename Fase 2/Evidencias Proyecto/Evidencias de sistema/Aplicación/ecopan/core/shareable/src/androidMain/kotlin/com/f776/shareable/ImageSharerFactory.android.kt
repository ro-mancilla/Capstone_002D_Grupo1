package com.f776.shareable

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import ecopan.core.shareable.generated.resources.Res
import ecopan.core.shareable.generated.resources.share_schedule_message
import org.jetbrains.compose.resources.getString
import java.io.File
import java.io.FileOutputStream


internal actual class ImageSharerFactory(
    private val context: Application,
    private val ioDispatcher: CoroutineDispatcher
) : ImageSharer {

    actual fun getImageSharer(): ImageSharer = this

    override suspend fun shareImage(
        byteArray: ByteArray,
        filename: String,
        extension: String,
        title: String
    ): String? = withContext(ioDispatcher) {
        try {
            // Create cache directory for sharing
            val cacheDir = File(context.cacheDir, "shared_images")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            // Create the image file
            val imageFile = File(cacheDir, "$filename.$extension")
            FileOutputStream(imageFile).use { outputStream ->
                outputStream.write(byteArray)
                outputStream.flush()
            }

            // Get URI using FileProvider
            val imageUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile
            )

            val shareMessage = getString(Res.string.share_schedule_message, title)
            // Create share intent
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, imageUri)
                putExtra(Intent.EXTRA_TEXT, shareMessage)
                type = "image/$extension"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }


            // Launch share sheet
            val chooserIntent = Intent.createChooser(shareIntent, shareMessage).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooserIntent)

            imageFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}