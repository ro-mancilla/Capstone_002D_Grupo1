@file:OptIn(ExperimentalTestApi::class)

package com.f776.core.designsystem

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runComposeUiTest
import com.f776.core.designsystem.preview.EcopanGallery
import kotlin.test.Test
import kotlin.test.assertTrue

class EcopanGalleryTest {

    @Test
    fun galleryComposesInLightAndDark() = runComposeUiTest {
        for (dark in listOf(false, true)) {
            setContent { EcopanGallery(darkTheme = dark) }
            waitForIdle()
            // Present in the semantic tree regardless of scroll position.
            assertTrue(onAllNodesWithText("Cobrar").fetchSemanticsNodes().isNotEmpty(), "dark=$dark: Cobrar")
            assertTrue(onAllNodesWithText("Estable").fetchSemanticsNodes().isNotEmpty(), "dark=$dark: Estable")
        }
    }
}
