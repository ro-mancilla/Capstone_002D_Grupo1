package com.f776.core.ui.components

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
actual fun BoxScope.VerticalScrollBar(
    modifier: Modifier,
    scrollState: LazyListState
) {
    VerticalScrollbar(
        modifier = modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        style = LocalScrollbarStyle.current.copy(
            unhoverColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
            hoverColor = MaterialTheme.colorScheme.primaryContainer
        ),
        adapter = rememberScrollbarAdapter(
            scrollState = scrollState
        )
    )
}