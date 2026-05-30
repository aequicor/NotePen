package ru.kyamshanov.notepen

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

/**
 * Mobile: подсказка показывается по ДОЛГОМУ нажатию и держится
 * [TOOLTIP_VISIBLE_MS] мс. Детектор работает в [PointerEventPass.Initial] и не
 * потребляет события до срабатывания таймаута, поэтому обычный тап беспрепятственно
 * доходит до [content] (кнопка срабатывает). При долгом нажатии остаток жеста
 * потребляется — кнопка под подсказкой НЕ активируется. Пустой [text] оставляет
 * [content] без обёртки.
 */
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
    var visible by remember { mutableStateOf(false) }
    val gapPx = with(LocalDensity.current) { TOOLTIP_GAP.roundToPx() }

    if (visible) {
        LaunchedEffect(Unit) {
            delay(TOOLTIP_VISIBLE_MS)
            visible = false
        }
    }

    Box(
        modifier =
            modifier.pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val longPressed =
                        try {
                            withTimeout(viewConfiguration.longPressTimeoutMillis) {
                                waitForUpOrCancellation(PointerEventPass.Initial)
                            }
                            // Отпустили/отменили раньше таймаута — это обычный тап, не подсказка.
                            false
                        } catch (_: TimeoutCancellationException) {
                            true
                        }
                    if (longPressed) {
                        visible = true
                        // Гасим остаток жеста, чтобы долгое нажатие не сработало как клик по кнопке.
                        consumeUntilUp()
                    }
                }
            },
    ) {
        content()
        if (visible) {
            Popup(
                popupPositionProvider = AboveAnchorPositionProvider(gapPx),
                onDismissRequest = { visible = false },
            ) {
                TooltipChip(text)
            }
        }
    }
}

/** Поглощает все события указателя в [PointerEventPass.Initial] до его отпускания. */
private suspend fun AwaitPointerEventScope.consumeUntilUp() {
    do {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        event.changes.forEach { it.consume() }
    } while (event.changes.any { it.pressed })
}

/**
 * Размещает подсказку по центру над якорем; если сверху не помещается — под ним.
 * По горизонтали прижимается к границам окна, чтобы плашка не уезжала за экран.
 */
private class AboveAnchorPositionProvider(
    private val gapPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val centeredX = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
        val x = centeredX.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val above = anchorBounds.top - popupContentSize.height - gapPx
        val y = if (above >= 0) above else anchorBounds.bottom + gapPx
        return IntOffset(x, y)
    }
}
