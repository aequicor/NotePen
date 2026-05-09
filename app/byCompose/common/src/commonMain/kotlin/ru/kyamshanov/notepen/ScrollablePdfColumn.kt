package ru.kyamshanov.notepen

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun ScrollablePdfColumn(
    state: LazyListState,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
)
