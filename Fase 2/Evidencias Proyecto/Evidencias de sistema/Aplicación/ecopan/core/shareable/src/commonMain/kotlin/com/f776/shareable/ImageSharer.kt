package com.f776.shareable

interface ImageSharer {
    suspend fun shareImage(byteArray: ByteArray, filename: String, extension: String, title: String): String?
}