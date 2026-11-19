package com.f776.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.f776.core.designsystem.theme.EcopanShapes
import com.f776.core.designsystem.theme.EcopanTheme

/**
 * A sellable product on the till: an [image] slot (the caller loads the photo; a sunken block shows
 * until it paints), the [name] and the [price] in the price colour. [badge] sits over the image
 * corner (a count in the cart, for example). Tapping adds it; the whole tile is the touch target.
 */
@Composable
fun ProductTile(
    name: String,
    price: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    badge: String? = null,
    image: @Composable BoxScope.() -> Unit = {},
) {
    val c = EcopanTheme.colors
    EcopanCard(
        modifier = modifier.disabledAlpha(enabled),
        onClick = if (enabled) onClick else null,
        contentPadding = PaddingValues(12.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(EcopanShapes.large)
                .background(c.sunken),
        ) {
            image()
            if (badge != null) {
                EcopanBadge(text = badge, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
            }
        }
        Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                name,
                style = EcopanTheme.typography.body.copy(fontWeight = FontWeight.Medium),
                color = c.inkStrong,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(price, style = EcopanTheme.typography.figure.copy(fontWeight = FontWeight.SemiBold), color = c.price, maxLines = 1)
        }
    }
}
