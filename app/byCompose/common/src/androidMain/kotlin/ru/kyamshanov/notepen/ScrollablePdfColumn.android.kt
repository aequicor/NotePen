package ru.kyamshanov.notepen

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
actual fun ScrollablePdfColumn(
    state: LazyListState,
    modifier: Modifier,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        state = state,
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}
