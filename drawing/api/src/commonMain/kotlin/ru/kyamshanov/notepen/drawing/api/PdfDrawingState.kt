package ru.kyamshanov.notepen.drawing.api

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPoint
import ru.kyamshanov.notepen.annotation.domain.model.ToolKind
import ru.kyamshanov.notepen.annotation.domain.model.EraserMode
import ru.kyamshanov.notepen.annotation.domain.model.EraserSettings
import ru.kyamshanov.notepen.annotation.domain.model.EraserShape
import ru.kyamshanov.notepen.annotation.domain.StrokeSimplifier
import ru.kyamshanov.notepen.annotation.domain.model.PageExtent
import ru.kyamshanov.notepen.annotation.domain.shape.ShapeRecognizer
import ru.kyamshanov.notepen.annotation.domain.shape.ShapeResampler

private const val EXTENT_GROW_PAD: Float = 0.4f

/**
 * Состояние рисования одной страницы PDF.
 *
 * Является shared-контрактом: рендерер (вьюер, лупа) читает, input-система пишет.
 * Все свойства — Compose snapshot-state, что обеспечивает zero-copy синхронизацию
 * между вкладками одного файла и между вьюером и лупой.
 */
public class PdfDrawingState {
    public var currentPaths: SnapshotStateList<DrawingPath> =
        mutableStateListOf()

    /**
     * Live (still-being-drawn) stroke samples. Append-only during a stroke;
     * cleared on [finishDrawing].
     */
    public val livePoints: SnapshotStateList<DrawingPoint> =
        mutableStateListOf()

    public var isDrawing: MutableState<Boolean> = mutableStateOf(false)
    public var strokeWidth: MutableState<Float> = mutableFloatStateOf(DrawingPath.DEFAULT_STROKE_WIDTH)

    /** ARGB-packed color of the active stroke; kept in sync with pen settings. */
    public var strokeColorArgb: MutableState<Long> = mutableLongStateOf(DrawingPath.BLACK_ARGB)

    /** Tool of the active stroke; written by the input layer before [startDrawing]. */
    public var strokeToolKind: MutableState<ToolKind> = mutableStateOf(ToolKind.PEN)

    /** Color of the currently-live stroke. Snapshotted at [startDrawing]. */
    public val liveColorArgb: MutableState<Long> =
        mutableStateOf(DrawingPath.BLACK_ARGB)

    /** Tool of the currently-live stroke. Snapshotted at [startDrawing]. */
    public val liveToolKind: MutableState<ToolKind> =
        mutableStateOf(ToolKind.PEN)

    /** Normalised stroke width of the currently-live stroke. Snapshotted at [startDrawing]. */
    public val liveStrokeWidth: MutableState<Float> =
        mutableStateOf(DrawingPath.DEFAULT_STROKE_WIDTH)

    /**
     * Monotonic counter bumped whenever [currentPaths] changes through one of
     * the public mutators. Consumers that cache rendered output of
     * completed strokes read it as an invalidation signal.
     */
    public val historyVersion: MutableState<Int> = mutableStateOf(0)

    /** Расширенная рисуемая область страницы (растёт при выходе пера за пределы PDF). */
    public val extent: MutableState<PageExtent> =
        mutableStateOf(PageExtent.Pdf)

    /** Позиция курсора ластика (`null` пока жест не активен). */
    public val eraserPos: MutableState<EraserPosition?> =
        mutableStateOf(null)

    private var gestureSnapped: Boolean = false

    /** Bump [historyVersion] to invalidate caches keyed on completed-stroke content. */
    public fun markHistoryChanged() {
        historyVersion.value++
    }

    /**
     * Расширить [extent], чтобы он включал точку `(x, y)` с запасом.
     * Возвращает `true`, если extent изменился.
     */
    public fun growExtentToInclude(x: Float, y: Float): Boolean {
        val current = extent.value
        val grown = current.including(x, y, pad = EXTENT_GROW_PAD)
        if (grown === current) return false
        extent.value = grown
        if (currentPaths.isNotEmpty()) markHistoryChanged()
        return true
    }

    /** Принудительно установить [extent] (загрузка, sync). */
    public fun setExtent(value: PageExtent) {
        if (extent.value == value) return
        extent.value = value
        markHistoryChanged()
    }

    /** Начать новый штрих. */
    public fun startDrawing(
        x: Float,
        y: Float,
        normalizedStrokeWidth: Float = strokeWidth.value,
        pressure: Float = 1f,
        tilt: Float = 0f,
    ) {
        isDrawing.value = true
        gestureSnapped = false
        liveColorArgb.value = strokeColorArgb.value
        liveToolKind.value = strokeToolKind.value
        liveStrokeWidth.value = normalizedStrokeWidth
        livePoints.clear()
        livePoints.add(DrawingPoint(x, y, isNewPath = true, pressure = pressure, tilt = tilt))
        growExtentToInclude(x, y)
    }

    /** Добавить точку к текущему штриху. */
    public fun addPoint(x: Float, y: Float, pressure: Float = 1f, tilt: Float = 0f) {
        if (isDrawing.value && !gestureSnapped) {
            livePoints.add(DrawingPoint(x, y, pressure = pressure, tilt = tilt))
            growExtentToInclude(x, y)
        }
    }

    /**
     * Пробует распознать live-штрих как фигуру и заменить [livePoints].
     *
     * @param pageAspect `pageWidthPx / pageHeightPx`
     * @return `true` если штрих заменён фигурой
     */
    public fun snapLiveStrokeToShape(pageAspect: Float): Boolean {
        if (!isDrawing.value || gestureSnapped || livePoints.size < 2) return false
        val snapshot = livePoints.toList()
        val shape = ShapeRecognizer.recognize(snapshot, pageAspect) ?: return false
        val avgPressure = snapshot.fold(0f) { acc, p -> acc + p.pressure } / snapshot.size
        val avgTilt = snapshot.fold(0f) { acc, p -> acc + p.tilt } / snapshot.size
        val replacement = ShapeResampler.toPoints(shape, avgPressure, avgTilt)
        livePoints.clear()
        livePoints.addAll(replacement)
        gestureSnapped = true
        for (p in replacement) growExtentToInclude(p.x, p.y)
        markHistoryChanged()
        return true
    }

    /** Завершить штрих и коммитить в [currentPaths]. Возвращает коммитнутый путь или `null`. */
    public fun finishDrawing(): DrawingPath? {
        val completed = if (isDrawing.value && livePoints.size > 1) {
            val raw = livePoints.toList()
            // Распознанные фигуры (gestureSnapped) уже минимальны — не прореживаем.
            val points = if (gestureSnapped) raw else StrokeSimplifier.simplify(raw)
            val path = DrawingPath(
                points = points,
                colorArgb = liveColorArgb.value,
                strokeWidth = liveStrokeWidth.value,
                toolType = liveToolKind.value,
            )
            currentPaths.add(path)
            markHistoryChanged()
            path
        } else {
            null
        }
        isDrawing.value = false
        gestureSnapped = false
        livePoints.clear()
        return completed
    }

    /**
     * Отменить текущий штрих, НЕ коммитя его в [currentPaths]. Используется,
     * когда жест прерван (например, начался pinch-zoom вторым пальцем) — в
     * отличие от [finishDrawing], незавершённый штрих не должен оставаться на
     * странице.
     */
    public fun discardDrawing() {
        isDrawing.value = false
        gestureSnapped = false
        livePoints.clear()
    }

    /** Очистить все штрихи и сбросить extent. */
    public fun clearDrawing() {
        currentPaths.clear()
        livePoints.clear()
        extent.value = PageExtent.Pdf
        markHistoryChanged()
    }

    /** Восстановить штрихи из снапшота (undo/redo, sync). */
    public fun restoreSnapshot(snapshot: List<DrawingPath>) {
        currentPaths.clear()
        currentPaths.addAll(snapshot)
        markHistoryChanged()
    }
}

/**
 * Point-based erase: удаляет точки внутри зоны, разделяя штрихи на суб-штрихи.
 *
 * @return `true` если хотя бы один штрих изменён или удалён
 */
public fun PdfDrawingState.erasePointsInZone(
    centerX: Float,
    centerY: Float,
    halfSizeNormalized: Float,
    shape: EraserShape,
    bumpHistory: Boolean = true,
): Boolean {
    if (currentPaths.isEmpty()) return false

    val r2 = halfSizeNormalized * halfSizeNormalized
    val rebuilt = ArrayList<DrawingPath>(currentPaths.size)
    var anyChange = false

    for (path in currentPaths) {
        val survivors = ArrayList<ArrayList<DrawingPoint>>()
        var current: ArrayList<DrawingPoint>? = null
        var pathChanged = false

        for (pt in path.points) {
            val dx = pt.x - centerX
            val dy = pt.y - centerY
            val inZone = when (shape) {
                EraserShape.CIRCLE -> dx * dx + dy * dy <= r2
                EraserShape.SQUARE -> kotlin.math.abs(dx) <= halfSizeNormalized &&
                        kotlin.math.abs(dy) <= halfSizeNormalized
            }
            if (inZone) {
                pathChanged = true
                current = null
            } else {
                if (current == null) {
                    current = ArrayList()
                    survivors.add(current)
                }
                current.add(pt)
            }
        }

        if (!pathChanged) {
            rebuilt.add(path)
            continue
        }

        anyChange = true
        for (group in survivors) {
            if (group.size < 2) continue
            val pts = ArrayList<DrawingPoint>(group.size)
            group.forEachIndexed { idx, p ->
                pts.add(p.copy(isNewPath = idx == 0))
            }
            rebuilt.add(path.copy(points = pts))
        }
    }

    if (anyChange) {
        replaceContentsInPlace(currentPaths, rebuilt)
        if (bumpHistory) markHistoryChanged()
    }
    return anyChange
}

private fun replaceContentsInPlace(
    target: SnapshotStateList<DrawingPath>,
    source: List<DrawingPath>,
) {
    val oldSize = target.size
    val newSize = source.size
    val min = if (oldSize < newSize) oldSize else newSize
    for (i in 0 until min) {
        if (target[i] !== source[i]) target[i] = source[i]
    }
    if (newSize > oldSize) {
        for (i in oldSize until newSize) target.add(source[i])
    } else if (newSize < oldSize) {
        for (i in oldSize - 1 downTo newSize) target.removeAt(i)
    }
}

/**
 * Object-mode erase: удаляет штрихи целиком, если хотя бы одна точка в зоне.
 *
 * @return `true` если хотя бы один штрих удалён
 */
public fun PdfDrawingState.eraseStrokesInZone(
    centerX: Float,
    centerY: Float,
    halfSizeNormalized: Float,
    shape: EraserShape,
    bumpHistory: Boolean = true,
): Boolean {
    if (currentPaths.isEmpty()) return false

    val r2 = halfSizeNormalized * halfSizeNormalized
    val before = currentPaths.size

    currentPaths.removeAll { path ->
        path.points.any { pt ->
            val dx = pt.x - centerX
            val dy = pt.y - centerY
            when (shape) {
                EraserShape.CIRCLE -> dx * dx + dy * dy <= r2
                EraserShape.SQUARE -> kotlin.math.abs(dx) <= halfSizeNormalized &&
                        kotlin.math.abs(dy) <= halfSizeNormalized
            }
        }
    }

    val changed = currentPaths.size != before
    if (changed && bumpHistory) markHistoryChanged()
    return changed
}

/**
 * Диспетчеризация стирания по [EraserSettings.mode].
 *
 * @return `true` если хотя бы один штрих изменён
 */
public fun PdfDrawingState.eraseInZone(
    centerX: Float,
    centerY: Float,
    halfSizeNormalized: Float,
    settings: EraserSettings,
    bumpHistory: Boolean = true,
): Boolean = when (settings.mode) {
    EraserMode.POINT -> erasePointsInZone(
        centerX, centerY, halfSizeNormalized, settings.shape, bumpHistory,
    )

    EraserMode.OBJECT -> eraseStrokesInZone(
        centerX, centerY, halfSizeNormalized, settings.shape, bumpHistory,
    )
}
