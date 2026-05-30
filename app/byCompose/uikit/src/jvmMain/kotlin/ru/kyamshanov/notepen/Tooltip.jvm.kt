package ru.kyamshanov.notepen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * Desktop: подсказка показывается при наведении мыши с задержкой
 * ([TOOLTIP_HOVER_DELAY_MS]) и держится, пока курсор остаётся над кнопкой —
 * стандартный desktop-tooltip через [TooltipArea]. Пустой [text] оставляет
 * [content] без обёртки.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
public actual fun Tooltip(
    text: String,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    if (text.isEmpty()) {
        content()
        return
    }
    TooltipArea(
        tooltip = { TooltipChip(text) },
        delayMillis = TOOLTIP_HOVER_DELAY_MS,
        tooltipPlacement =
            TooltipPlacement.ComponentRect(
                anchor = Alignment.TopCenter,
                alignment = Alignment.BottomCenter,
                offset = DpOffset(0.dp, -TOOLTIP_GAP),
            ),
        modifier = modifier,
        content = content,
    )
}
