package ru.kyamshanov.notepen.pdfviewer

import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.graphics.ImageBitmap

/**
 * LRU-кэш отрендеренных страниц PDF, ключ — индекс страницы. Хранит
 * последний доступный битмап независимо от текущего масштаба — он
 * показывается мгновенно, пока фоновый рендер готовит более резкий
 * вариант. Эвикция по числу записей, чтобы не тянуть платформенный
 * API для измерения байт.
 *
 * Используется как backing-store для [SnapshotStateMap], так что
 * Compose автоматически перекомпонует страницу при появлении нового
 * битмапа.
 */
internal data class RenderedPage(
    val bitmap: ImageBitmap,
    val renderedAtScalePercent: Int,
)

internal class PdfBitmapCache(private val maxEntries: Int) {

    private val accessOrder = ArrayDeque<Int>()

    /**
     * Снапшот-стейт, на который подписан UI: запись в него тут же
     * триггерит рекомпозицию страниц, в которых битмап сменился.
     */
    val entries: SnapshotStateMap<Int, RenderedPage> = SnapshotStateMap()

    fun get(pageIndex: Int): RenderedPage? {
        val r = entries[pageIndex] ?: return null
        touch(pageIndex)
        return r
    }

    fun put(pageIndex: Int, rendered: RenderedPage) {
        if (entries.containsKey(pageIndex)) {
            entries[pageIndex] = rendered
            touch(pageIndex)
            return
        }
        entries[pageIndex] = rendered
        accessOrder.addLast(pageIndex)
        evictIfNeeded()
    }

    private fun touch(pageIndex: Int) {
        accessOrder.remove(pageIndex)
        accessOrder.addLast(pageIndex)
    }

    private fun evictIfNeeded() {
        while (accessOrder.size > maxEntries) {
            val evicted = accessOrder.removeFirst()
            entries.remove(evicted)
        }
    }

    fun clear() {
        accessOrder.clear()
        entries.clear()
    }
}
