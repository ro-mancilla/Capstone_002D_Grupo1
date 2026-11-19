package com.f776.core.designsystem.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.f776.core.designsystem.theme.EcopanTheme

/**
 * Every token and component of the design system on one scrolling page, in [darkTheme] or light.
 * Every new component gets a gallery entry here (`ControlsGallery` for inputs and actions,
 * `DomainGallery` for the till and baker components) so light and dark are both checked. The
 * `EcopanGalleryTest` composes the whole page for both themes on the JVM.
 */
@Composable
fun EcopanGallery(darkTheme: Boolean) {
    EcopanTheme(darkTheme = darkTheme) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(EcopanTheme.colors.page)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(if (darkTheme) "Ecopan · oscuro" else "Ecopan · claro", style = EcopanTheme.typography.headline, color = EcopanTheme.colors.inkStrong)
            ThemeGallery()
            ControlsGallery()
            DomainGallery()
        }
    }
}

/** A labelled group in a gallery page. */
@Composable
internal fun GallerySection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(title, style = EcopanTheme.typography.eyebrow, color = EcopanTheme.colors.inkMuted)
        content()
    }
}

@Preview(name = "Gallery light", widthDp = 900, heightDp = 2400)
@Composable
private fun EcopanGalleryLightPreview() = EcopanGallery(darkTheme = false)

@Preview(name = "Gallery dark", widthDp = 900, heightDp = 2400)
@Composable
private fun EcopanGalleryDarkPreview() = EcopanGallery(darkTheme = true)

@Preview(name = "Gallery side by side", widthDp = 1800, heightDp = 2400)
@Composable
private fun EcopanGalleryPairPreview() {
    Row {
        Column(Modifier.weight(1f)) { EcopanGallery(darkTheme = false) }
        Column(Modifier.weight(1f)) { EcopanGallery(darkTheme = true) }
    }
}
