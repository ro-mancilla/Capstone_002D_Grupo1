package com.f776.core.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun BoxScope.VerticalScrollBar(modifier: Modifier = Modifier, scrollState: LazyListState)