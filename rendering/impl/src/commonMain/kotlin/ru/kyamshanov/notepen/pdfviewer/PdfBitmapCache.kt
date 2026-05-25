package ru.kyamshanov.notepen.pdfviewer

import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.graphics.ImageBitmap

/**
 * LRU-кэш отрендеренных страниц PDF, ключ — индекс страницы. Хранит
 * последний доступный битмап независимо от текущего масштаба — он
 * показывается мгновенно, пока фоновый рендер готовит более резкий
 * вариант.
 *
 * Эвикция по двум лимитам: число записей ([maxEntries]) и суммарный
 * объём в пикселях ([maxTotalPixels]). Второй лимит критичен при
 * резкой смене масштаба: 4000×4000 битмап = 64 МБ, и накопить даже
 * 6 таких — уже под 400 МБ. Дополнительно [evictStaleScale]
 * проактивно выкидывает off-screen битмапы с давно неактуальным
 * масштабом.
 *
 * [SnapshotStateMap] как backing-store: запись триггерит рекомпозицию
 * страницы, в которой битмап сменился.
 */
data class RenderedPage(
    val bitmap: ImageBitmap,
    val renderedAtScalePercent: Int,
)

class PdfBitmapCache(
    private val maxEntries: Int,
    private val maxTotalPixels: Long = DEFAULT_MAX_PIXELS,
) {
    private val accessOrder = ArrayDeque<Int>()

    val entries: SnapshotStateMap<Int, RenderedPage> = SnapshotStateMap()

    fun get(pageIndex: Int): RenderedPage? {
        val r = entries[pageIndex] ?: return null
        touch(pageIndex)
        return r
    }

    fun put(
        pageIndex: Int,
        rendered: RenderedPage,
    ) {
        if (entries.containsKey(pageIndex)) {
            entries[pageIndex] = rendered
            touch(pageIndex)
        } else {
            entries[pageIndex] = rendered
            accessOrder.addLast(pageIndex)
        }
        evictIfNeeded(protect = setOf(pageIndex))
    }

    /**
     * Активно вытесняет off-screen записи, у которых отрисованный масштаб
     * значительно МЕНЬШЕ текущего ([currentScale] / [maxScaleRatio] или
     * меньше). Эти битмапы при zoom-in покажут blurry картинку → их
     * можно безопасно удалить и перерендерить.
     *
     * Битмапы с масштабом БОЛЬШЕ текущего НЕ вытесняются — Skia downsample
     * прекрасно работает, и сохранённый high-res вариант пригодится при
     * следующем zoom-in без долгой растеризации. Их объём ограничивается
     * pixel-ceiling LRU отдельно.
     *
     * Видимые страницы ([visibleIndices]) защищены — их битмап
     * продолжает показываться, пока новый не отрендерится.
     */
    fun evictStaleScale(
        visibleIndices: Set<Int>,
        currentScale: Int,
        maxScaleRatio: Float,
    ) {
        if (currentScale <= 0) return
        val toRemove = mutableListOf<Int>()
        for ((idx, rendered) in entries) {
            if (idx in visibleIndices) continue
            val cachedScale = rendered.renderedAtScalePercent.toFloat()
            // Удаляем только "too low-res" варианты. cachedScale большой →
            // оставляем (он годится для отображения при любом меньшем зуме).
            if (cachedScale * maxScaleRatio < currentScale) toRemove.add(idx)
        }
        for (idx in toRemove) {
            entries.remove(idx)
            accessOrder.remove(idx)
        }
    }

    private fun touch(pageIndex: Int) {
        accessOrder.remove(pageIndex)
        accessOrder.addLast(pageIndex)
    }

    /**
     * Эвикция до удовлетворения обоих лимитов. [protect] — индексы,
     * которые нельзя удалять (как минимум только что положенная запись,
     * чтобы не вытеснить саму себя сразу после put).
     */
    private fun evictIfNeeded(protect: Set<Int>) {
        while (accessOrder.size > maxEntries) {
            val evicted = accessOrder.firstOrNull { it !in protect } ?: break
            accessOrder.remove(evicted)
            entries.remove(evicted)
        }
        while (totalPixels() > maxTotalPixels) {
            val evicted = accessOrder.firstOrNull { it !in protect } ?: break
            accessOrder.remove(evicted)
            entries.remove(evicted)
        }
    }

    private fun totalPixels(): Long {
        var total = 0L
        for ((_, r) in entries) total += r.bitmap.width.toLong() * r.bitmap.height
        return total
    }

    fun clear() {
        accessOrder.clear()
        entries.clear()
    }

    companion object {
        /** ≈256 МБ при 4 байт/пиксель — комфортный потолок для desktop. */
        const val DEFAULT_MAX_PIXELS: Long = 64_000_000L
    }
}
