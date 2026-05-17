package ru.kyamshanov.notepen.magnifier

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Состояние инструмента «лупа для письма».
 *
 * Лупа состоит из двух связанных UI-частей:
 *  - **target frame** — небольшая прямоугольная рамка на странице,
 *    обозначающая, какая часть страницы попадает под увеличение;
 *  - **input panel** — плавающее окно поверх UI, в котором содержимое
 *    `targetRect` отображается крупно и в которое пользователь пишет пером.
 *
 * Все штрихи коммитятся напрямую в `PdfDrawingState` той страницы, на
 * которой находится рамка — magnifier лишь маппит координаты pointer-входа
 * из панели в page-space. Это сохраняет одну точку истины для штрихов и
 * корректно работает с существующим undo/redo, sync и сохранением.
 *
 * Поведение пропорций: `panelSize` и `targetRect` (в page-pixels) должны
 * иметь одинаковый aspect-ratio — иначе содержимое искажалось бы при
 * масштабировании. Любое изменение одного из размеров перерасчитывает
 * другой так, чтобы выровнять пропорции (см. [resizePanel] / [resizeTarget]).
 */
class MagnifierState {

    /** Включён ли инструмент. */
    var enabled: Boolean by mutableStateOf(false)
        private set

    /**
     * Индекс страницы, к которой привязана рамка-цель. Изменяется только
     * через [enable] / [moveToPage] (последнее — out of scope для v1).
     */
    var pageIndex: Int by mutableStateOf(0)
        private set

    /**
     * Область страницы (page-normalized [0..1]), отображаемая в панели.
     * Изменяется через [moveTarget], [resizeTarget], [shiftTargetForAutoscroll].
     */
    var targetRect: Rect by mutableStateOf(DEFAULT_TARGET)
        private set

    /**
     * Положение левого-верхнего угла плавающей панели, в пикселях вьюпорта.
     * Двигается через [movePanel].
     */
    var panelTopLeft: Offset by mutableStateOf(Offset.Zero)
        private set

    /** Размер плавающей панели в пикселях. */
    var panelSize: Size by mutableStateOf(DEFAULT_PANEL_SIZE)
        private set

    /**
     * Размер canvas страницы (в пикселях вьюпорта) для активной страницы.
     * Записывается из `DrawablePdfPage` через [updatePageCanvasPx]. Нужен,
     * чтобы рассчитать `normalizedStrokeWidth = penSettings.strokeWidth /
     * pageCanvasPx.width` так же, как это делает обычный pen-pipeline.
     */
    var pageCanvasWidthPx: Float by mutableStateOf(0f)
        private set

    /**
     * Последний доступный битмап активной страницы. Подаётся из
     * `pageContent` в `DetailsContent`. Используется панелью как источник
     * для отрисовки фонового тайла.
     */
    var pageBitmap: ImageBitmap? by mutableStateOf(null)
        private set

    /** Включена ли авто-прокрутка рамки при подходе пера к правому краю панели. */
    var autoScrollEnabled: Boolean by mutableStateOf(true)
        private set

    // --- mutators -----------------------------------------------------------

    /**
     * Включить лупу на странице [onPage], разместив рамку и панель по
     * умолчанию. [viewportSize] нужен, чтобы припарковать панель внизу
     * экрана.
     */
    fun enable(onPage: Int, viewportSize: Size) {
        pageIndex = onPage
        targetRect = DEFAULT_TARGET
        val panelW = (viewportSize.width * 0.6f).coerceAtLeast(MIN_PANEL_DIM_PX)
        val panelH = panelW * DEFAULT_TARGET.height / DEFAULT_TARGET.width
        panelSize = Size(panelW, panelH)
        panelTopLeft = Offset(
            x = (viewportSize.width - panelW) * 0.5f,
            y = (viewportSize.height - panelH - PANEL_BOTTOM_MARGIN_PX).coerceAtLeast(0f),
        )
        enabled = true
    }

    /** Выключить лупу. */
    fun disable() {
        enabled = false
    }

    fun toggleAutoScroll() {
        autoScrollEnabled = !autoScrollEnabled
    }

    /**
     * Сдвинуть рамку-цель на [deltaPageSpace] (page-normalized). Размер
     * сохраняется; результат клампится к `[0..1]`.
     */
    fun moveTarget(deltaPageSpace: Offset) {
        val r = targetRect
        targetRect = clampTargetToPage(
            Rect(
                left = r.left + deltaPageSpace.x,
                top = r.top + deltaPageSpace.y,
                right = r.right + deltaPageSpace.x,
                bottom = r.bottom + deltaPageSpace.y,
            ),
        )
    }

    /**
     * Изменить размер рамки. Сохраняет позицию левого-верхнего угла; новые
     * размеры клампятся к `[0..1]` (с учётом [MIN_TARGET_DIM]). Aspect
     * панели автоматически подстраивается под новый aspect рамки.
     */
    fun resizeTarget(newWidth: Float, newHeight: Float) {
        val r = targetRect
        val clamped = clampTargetToPage(
            Rect(
                left = r.left,
                top = r.top,
                right = r.left + newWidth,
                bottom = r.top + newHeight,
            ),
        )
        targetRect = clamped
        alignPanelAspectToTarget()
    }

    /** Сдвинуть плавающую панель на [delta] (viewport-пиксели). */
    fun movePanel(delta: Offset) {
        panelTopLeft = panelTopLeft + delta
    }

    /**
     * Изменить размер панели. Пропорции рамки-цели подстраиваются под
     * новый aspect панели (рамка не пересчитывает свою левую-верхнюю
     * точку; меняется только высота).
     */
    fun resizePanel(newSize: Size) {
        val w = newSize.width.coerceAtLeast(MIN_PANEL_DIM_PX)
        val h = newSize.height.coerceAtLeast(MIN_PANEL_DIM_PX)
        panelSize = Size(w, h)
        alignTargetAspectToPanel()
    }

    /**
     * Обновить размер canvas страницы. Вызывается из `DrawablePdfPage`
     * при изменении его размера, пока magnifier активен на этой странице.
     */
    fun updatePageCanvasPx(widthPx: Float) {
        pageCanvasWidthPx = widthPx
    }

    /** Обновить ссылку на битмап активной страницы. */
    fun updatePageBitmap(bitmap: ImageBitmap?) {
        pageBitmap = bitmap
    }

    /**
     * Сдвиг рамки после завершения штриха в указанном направлении
     * (Scribble-like). См. [AutoScrollDir].
     *
     * Возвращает `true`, если сдвиг был выполнен (т.е. рамка не упёрлась
     * в нижний край страницы).
     */
    fun shiftTargetForAutoscroll(direction: AutoScrollDir): Boolean {
        val r = targetRect
        val w = r.right - r.left
        val h = r.bottom - r.top
        return when (direction) {
            AutoScrollDir.RIGHT -> {
                val shifted = r.left + w * AUTO_SCROLL_ADVANCE
                if (shifted + w <= 1f - EDGE_EPS) {
                    targetRect = Rect(shifted, r.top, shifted + w, r.bottom)
                    true
                } else {
                    // Перевод строки.
                    val newTop = r.top + h * AUTO_SCROLL_LINE_FEED
                    if (newTop + h > 1f) return false
                    targetRect = Rect(LINE_LEFT_MARGIN, newTop, LINE_LEFT_MARGIN + w, newTop + h)
                    true
                }
            }
        }
    }

    // --- private helpers ----------------------------------------------------

    private fun alignPanelAspectToTarget() {
        val r = targetRect
        val tw = r.right - r.left
        val th = r.bottom - r.top
        if (tw <= 0f || th <= 0f) return
        val newPanelH = panelSize.width * th / tw
        panelSize = Size(panelSize.width, newPanelH.coerceAtLeast(MIN_PANEL_DIM_PX))
    }

    private fun alignTargetAspectToPanel() {
        val r = targetRect
        val tw = r.right - r.left
        if (tw <= 0f || panelSize.width <= 0f) return
        val targetH = tw * panelSize.height / panelSize.width
        val newRect = clampTargetToPage(
            Rect(r.left, r.top, r.right, r.top + targetH),
        )
        targetRect = newRect
    }

    private companion object {
        val DEFAULT_TARGET = Rect(0.4f, 0.4f, 0.6f, 0.45f)
        val DEFAULT_PANEL_SIZE = Size(800f, 200f)
        const val MIN_PANEL_DIM_PX = 120f
        const val PANEL_BOTTOM_MARGIN_PX = 24f

        /** Доля ширины рамки, на которую сдвигаемся вправо (85% — остаётся 15% перехлёста). */
        const val AUTO_SCROLL_ADVANCE = 0.85f

        /** Доля высоты рамки, на которую опускаемся при переводе строки. */
        const val AUTO_SCROLL_LINE_FEED = 0.85f

        /** Левый отступ строки при wrap'е. */
        const val LINE_LEFT_MARGIN = 0.05f

        /** Эпсилон для предотвращения дребезга на самой границе. */
        const val EDGE_EPS = 1e-4f
    }
}

/** Направление авто-прокрутки рамки при подходе пера к краю панели. */
enum class AutoScrollDir { RIGHT }
