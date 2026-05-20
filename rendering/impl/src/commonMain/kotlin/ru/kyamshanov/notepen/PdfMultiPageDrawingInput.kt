package ru.kyamshanov.notepen

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import ru.kyamshanov.notepen.tablet.TabletInputController

/**
 * Pointer-input overlay, лифтнутый над PdfPagesViewer. Захватывает
 * стилус-жесты (на Android — с palm-rejection; на десктопе — мышь как
 * стилус) и делегирует их [controller], который сам разруливает переходы
 * между страницами.
 *
 * Не консумит touch-DOWN на Android при активной palm-rejection — palm/pan
 * по-прежнему попадают в viewer (scrollable / pinch).
 */
fun Modifier.pdfMultiPageDrawingInput(
    controller: MultiPageDrawingController,
    tablet: TabletInputController,
    palmRejectionActive: () -> Boolean,
    captureGesture: (Offset) -> Boolean = { false },
): Modifier = pdfMultiPageDrawingInput(
    key = controller,
    tablet = tablet,
    palmRejectionActive = palmRejectionActive,
    captureGesture = captureGesture,
    onDown = controller::onDown,
    onMove = controller::onMove,
    onUp = controller::onUp,
    onCancel = controller::onCancel,
)

/**
 * Перегрузка с явными колбэками — используется, когда события маршрутизируются
 * между несколькими получателями (например, drawing vs loupe selection). [key]
 * задаёт identity для `pointerInput`: смена ключа пересоздаёт обработчик и
 * обрывает активный жест.
 */
fun Modifier.pdfMultiPageDrawingInput(
    key: Any?,
    tablet: TabletInputController,
    palmRejectionActive: () -> Boolean,
    captureGesture: (Offset) -> Boolean = { false },
    onDown: (Offset, Float, Float) -> Unit,
    onMove: (Offset, Float, Float) -> Unit,
    onUp: () -> Unit,
    onCancel: () -> Unit,
): Modifier = this.pointerInput(key) {
    detectStylusAwareDrag(
        tablet = tablet,
        isPalmRejectionActive = palmRejectionActive,
        captureGesture = captureGesture,
        onDown = onDown,
        onMove = onMove,
        onUp = onUp,
        onCancel = onCancel,
    )
}
