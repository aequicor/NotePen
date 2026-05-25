package ru.kyamshanov.notepen

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Детектор удержания pointer'а на месте во время рисующего жеста.
 *
 * Используется для shape-recognition (см. [PdfDrawingState.snapLiveStrokeToShape]).
 * Запускает таймер на [delayMs]; если pointer заметно сдвинулся (расстояние от
 * якоря превысило [toleranceNorm]), таймер перезапускается с новым якорем.
 * Если выдержано [delayMs] без движения — вызывается [onHold].
 *
 * Координаты — нормализованные `[0..1]` относительно страницы. Для учёта
 * аспект-рации (округление dx с поправкой) принимается коэффициент [aspect]
 * = `pageWidthPx / pageHeightPx`.
 */
class HoldGestureTracker(
    private val scope: CoroutineScope,
    private val delayMs: Long,
    private val toleranceNorm: Float,
    private val onHold: () -> Unit,
) {
    private var job: Job? = null
    private var anchorX: Float = 0f
    private var anchorY: Float = 0f
    private var anchorAspect: Float = 1f

    /** Начало жеста — устанавливает якорь и запускает таймер. */
    fun onDown(
        x: Float,
        y: Float,
        aspect: Float,
    ) {
        anchorX = x
        anchorY = y
        anchorAspect = if (aspect > 0f) aspect else 1f
        restart()
    }

    /**
     * Очередной sample. Если pointer ушёл с якоря дальше [toleranceNorm] в
     * физическом пространстве — таймер перезапускается с этой позиции в роли
     * нового якоря.
     */
    fun onMove(
        x: Float,
        y: Float,
    ) {
        val dx = (x - anchorX) * anchorAspect
        val dy = y - anchorY
        if (sqrt(dx * dx + dy * dy) > toleranceNorm) {
            anchorX = x
            anchorY = y
            restart()
        }
    }

    /** Конец/отмена жеста — таймер гасится. */
    fun cancel() {
        job?.cancel()
        job = null
    }

    private fun restart() {
        job?.cancel()
        job =
            scope.launch {
                delay(delayMs)
                onHold()
            }
    }
}
