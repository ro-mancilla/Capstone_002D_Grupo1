package com.f776.capturable

import androidx.compose.ui.graphics.ImageBitmap

expect fun ImageBitmap.toByteArray(
    compressionFormat: CompressionFormat,
    quality: Int
): ByteArray

enum class CompressionFormat {
    JPEG, PNG
}