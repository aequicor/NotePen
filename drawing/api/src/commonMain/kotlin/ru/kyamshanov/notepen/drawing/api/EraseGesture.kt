package ru.kyamshanov.notepen.drawing.api

import androidx.compose.runtime.MutableState
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.annotation.domain.model.EraserSettings

/**
 * Сессия стирания над одной [PdfDrawingState].
 *
 * Доменная логика жеста ластика: снимает pre-снапшот, прогоняет
 * [PdfDrawingState.eraseInZone] по точкам/штрихам с троттлингом
 * history-bump'ов и уведомляет о завершении. Не зависит от рендеринга
 * и Compose-UI — только snapshot-state из `compose.runtime`.
 */
class EraseGesture(
    private val pdfDrawingState: PdfDrawingState,
    private val eraserSettings: EraserSettings,
    private val eraserPos: MutableState<EraserPosition?>,
    private val onGestureStart: (snapshot: List<DrawingPath>) -> Unit,
    private val onEraseFinished: (before: List<DrawingPath>, after: List<DrawingPath>) -> Unit,
) {
    private var preEraseSnapshot: List<DrawingPath> = emptyList()
    private var lastEraseX = Float.NaN
    private var lastEraseY = Float.NaN
    private val halfSize = eraserSettings.sizeNormalized / 2f
    private val moveThresholdSq = (halfSize * 0.25f).let { it * it }
    private var lastHistoryBump = kotlin.time.TimeSource.Monotonic.markNow()
    private var pendingBump = false

    fun start(nx: Float, ny: Float) {
        preEraseSnapshot = pdfDrawingState.currentPaths.toList()
        onGestureStart(preEraseSnapshot)
        eraserPos.value = EraserPosition(nx, ny)
        lastEraseX = nx
        lastEraseY = ny
        val changed = pdfDrawingState.eraseInZone(
            centerX = nx,
            centerY = ny,
            halfSizeNormalized = halfSize,
            settings = eraserSettings,
            bumpHistory = false,
        )
        if (changed) {
            pdfDrawingState.markHistoryChanged()
            lastHistoryBump = kotlin.time.TimeSource.Monotonic.markNow()
            pendingBump = false
        }
    }

    fun move(nx: Float, ny: Float) {
        eraserPos.value = EraserPosition(nx, ny)
        val dx = nx - lastEraseX
        val dy = ny - lastEraseY
        if (dx * dx + dy * dy < moveThresholdSq) return
        lastEraseX = nx
        lastEraseY = ny
        val changed = pdfDrawingState.eraseInZone(
            centerX = nx,
            centerY = ny,
            halfSizeNormalized = halfSize,
            settings = eraserSettings,
            bumpHistory = false,
        )
        if (changed) {
            pendingBump = true
            if (lastHistoryBump.elapsedNow().inWholeMilliseconds >= 80L) {
                pdfDrawingState.markHistoryChanged()
                lastHistoryBump = kotlin.time.TimeSource.Monotonic.markNow()
                pendingBump = false
            }
        }
    }

    fun end() {
        eraserPos.value = null
        if (pendingBump) {
            pdfDrawingState.markHistoryChanged()
            pendingBump = false
        }
        onEraseFinished(preEraseSnapshot, pdfDrawingState.currentPaths.toList())
    }

    fun cancel() {
        eraserPos.value = null
        if (pendingBump) {
            pdfDrawingState.markHistoryChanged()
            pendingBump = false
        }
        // Жест мог быть отменён pointerInput-restart'ом (смена ключей,
        // рекомпозиция родителя) уже после того, как eraseInZone реально
        // изменил currentPaths. Если не уведомить sync — пир остаётся со
        // старыми штрихами. Сравниваем по identity: ничего не стёрто →
        // ничего не шлём.
        val after = pdfDrawingState.currentPaths.toList()
        if (after.size != preEraseSnapshot.size ||
            after.zip(preEraseSnapshot).any { (a, b) -> a !== b }
        ) {
            onEraseFinished(preEraseSnapshot, after)
        }
    }
}
