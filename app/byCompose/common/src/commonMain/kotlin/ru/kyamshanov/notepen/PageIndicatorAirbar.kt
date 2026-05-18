package ru.kyamshanov.notepen

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.kyamshanov.notepen.ui.glass.GlassSurface

@Composable
fun PageIndicatorAirbar(
    currentPage: Int,
    totalPages: Int,
    modifier: Modifier = Modifier,
) {
    GlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(AIRBAR_CORNER_RADIUS),
    ) {
        Text(
            text = "Страница $currentPage / $totalPages",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(
                horizontal = AIRBAR_PADDING_H,
                vertical = AIRBAR_PADDING_V,
            ),
        )
    }
}

private val AIRBAR_CORNER_RADIUS = 12.dp
private val AIRBAR_PADDING_H = 16.dp
private val AIRBAR_PADDING_V = 8.dp
