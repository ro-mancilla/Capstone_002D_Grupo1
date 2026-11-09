package com.f776.core.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent

/**
 * A product photo, or the placeholder that stands in for one.
 *
 * Every state resolves to *something the same size*. A catalog photo is served from whatever URL a
 * tenant put in the row — a host that is slow, unreachable from the bakehouse, or simply wrong —
 * and a till whose tiles change height while the network makes up its mind is a till that mis-taps.
 * So loading, error and "no URL at all" all render the same bread glyph on the same ground, and
 * only a decoded image ever replaces it.
 *
 * [contentDescription] is null by design at every call site so far: the product's name is always
 * rendered next to the picture, and announcing "photo of Marraqueta, Marraqueta" is worse than
 * announcing it once.
 */
@Composable
fun ProductImage(
    imageUrl: String?,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl.isNullOrBlank()) {
            ProductImagePlaceholder()
            return@Box
        }

        SubcomposeAsyncImage(
            model = imageUrl,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        ) {
            // Crossfade rather than Coil's own `crossfade(true)`: this fades *between the two
            // composables*, so the placeholder is still drawn underneath while the photo arrives
            // and the tile never flashes its empty ground.
            val state by painter.state.collectAsState()
            Crossfade(state is AsyncImagePainter.State.Success) { decoded ->
                if (decoded) {
                    SubcomposeAsyncImageContent(modifier = Modifier.fillMaxSize())
                } else {
                    ProductImagePlaceholder()
                }
            }
        }
    }
}

@Composable
private fun ProductImagePlaceholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = Icons.Filled.BakeryDining,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
    }
}
