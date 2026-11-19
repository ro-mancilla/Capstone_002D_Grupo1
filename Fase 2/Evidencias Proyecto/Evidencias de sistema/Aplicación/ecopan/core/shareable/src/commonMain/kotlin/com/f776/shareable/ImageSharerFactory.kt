package com.f776.shareable

internal expect class ImageSharerFactory {
    fun getImageSharer(): ImageSharer
}