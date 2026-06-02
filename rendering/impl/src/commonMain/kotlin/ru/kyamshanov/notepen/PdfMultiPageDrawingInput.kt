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
    nativePositionOffset: () -> Offset = { Offset.Zero },
    nativePenInputEnabled: Boolean = true,
    fallbackDrawingPointerEnabled: () -> Boolean = { true },
): Modifier =
    pdfMultiPageDrawingInput(
        key = controller,
        tablet = tablet,
        palmRejectionActive = palmRejectionActive,
        captureGesture = captureGesture,
        nativePositionOffset = nativePositionOffset,
        nativePenInputEnabled = nativePenInputEnabled,
        fallbackDrawingPointerEnabled = fallbackDrawingPointerEnabled,
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
    nativePositionOffset: () -> Offset = { Offset.Zero },
    nativePenInputEnabled: Boolean = true,
    fallbackDrawingPointerEnabled: () -> Boolean = { true },
    onDown: (Offset, Float, Float) -> Unit,
    onMove: (Offset, Float, Float) -> Unit,
    onUp: () -> Unit,
    onCancel: () -> Unit,
): Modifier =
    this
        .then(
            if (nativePenInputEnabled) {
                Modifier.pointerInput(key, tablet) {
                    detectNativePenDrag(
                        tablet = tablet,
                        positionOffset = nativePositionOffset,
                        onDown = onDown,
                        onMove = onMove,
                        onUp = onUp,
                        onCancel = onCancel,
                    )
                }
            } else {
                Modifier
            },
        )
        .pointerInput(key) {
            detectStylusAwareDrag(
                tablet = tablet,
                isPalmRejectionActive = palmRejectionActive,
                captureGesture = captureGesture,
                acceptDrawingPointer = fallbackDrawingPointerEnabled,
                onDown = onDown,
                onMove = onMove,
                onUp = onUp,
                onCancel = onCancel,
            )
        }
