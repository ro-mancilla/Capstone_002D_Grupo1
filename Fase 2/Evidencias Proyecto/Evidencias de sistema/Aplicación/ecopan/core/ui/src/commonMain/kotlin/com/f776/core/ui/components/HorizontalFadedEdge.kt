package com.f776.core.ui.components


import androidx.annotation.FloatRange
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import com.f776.core.designsystem.component.ChipRow
import com.f776.core.designsystem.theme.EcopanTheme


@Composable
fun HorizontalFadedEdge(
    modifier: Modifier = Modifier,
    @FloatRange(from = 0.0, to = 1.0) startFrom: Float,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Black, Color.Transparent),
                        startX = size.width * startFrom,
                        endX = size.width
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
    ) {
        content()
    }
}

@Preview
@Composable
private fun HorizontalFadedEdgePreview() {
    EcopanTheme {
        Surface {
            HorizontalFadedEdge(
                startFrom = 0.95f
            ) {
                ChipRow(
                    items = listOf("Verb", "Noun", "Adjective", "Particle", "Preposition")
                )
            }
        }
    }
}