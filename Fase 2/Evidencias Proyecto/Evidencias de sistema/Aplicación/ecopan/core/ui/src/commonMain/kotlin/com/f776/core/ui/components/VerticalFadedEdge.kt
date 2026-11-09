package com.f776.core.ui.components

import androidx.annotation.FloatRange
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.f776.core.designsystem.theme.EcopanTheme

@Composable
fun VerticalFadedEdge(
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
                    brush = Brush.verticalGradient(
                        listOf(Color.Black, Color.Transparent),
                        startY = 0f,
                        endY = startFrom * size.height
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
    ) {
        content()
    }
}

@Preview
@PreviewLightDark
@Composable
private fun VerticalFadedEdgePreview() {
    EcopanTheme {
        Surface {
            VerticalFadedEdge(
                startFrom = 1f
            ) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(listOf("Verb", "Noun", "Adjective", "Particle", "Preposition")) {
                        SuggestionChip(
                            onClick = {},
                            label = { Text(it) },
                        )
                    }
                }
            }
        }
    }
}