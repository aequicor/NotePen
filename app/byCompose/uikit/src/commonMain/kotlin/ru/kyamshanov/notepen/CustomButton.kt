package ru.kyamshanov.notepen

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun CustomButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        shape = AppTheme.shapes.component,
        onClick = onClick,
        modifier = modifier,
        content = content,
    )
}
