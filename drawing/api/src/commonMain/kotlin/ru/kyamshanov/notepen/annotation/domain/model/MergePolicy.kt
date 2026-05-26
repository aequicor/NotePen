package ru.kyamshanov.notepen.annotation.domain.model

/**
 * Strategy for combining several [AnnotationLayer]s — this device's notes,
 * peers' layers, the host's baseline — into one set of pages, given an
 * [AnnotationFilter] deciding which strokes qualify.
 *
 * Separating *policy* (how to combine) from *filter* (what to include) leaves
 * room for future rules (e.g. per-author precedence, redaction) as new policies
 * rather than edits to call sites. Today the only policy is [UnionAllPolicy].
 */
interface MergePolicy {
    /**
     * Composes [layers] into strokes-by-page, keeping only strokes that satisfy
     * [filter].
     */
    fun compose(
        layers: List<AnnotationLayer>,
        filter: AnnotationFilter,
    ): Map<Int, List<DrawingPath>>
}

/**
 * Unions every qualifying stroke across all layers, de-duplicating by
 * [DrawingPath.strokeId]; strokes with an empty id (legacy, pre-sync) are always
 * kept. Layer order is preserved, so the resulting per-page z-order follows the
 * order layers were accumulated. This reproduces the pre-layer flat-merge.
 */
object UnionAllPolicy : MergePolicy {
    override fun compose(
        layers: List<AnnotationLayer>,
        filter: AnnotationFilter,
    ): Map<Int, List<DrawingPath>> {
        val out = mutableMapOf<Int, MutableList<DrawingPath>>()
        val seenStrokeIds = mutableSetOf<String>()
        for (layer in layers) {
            for ((pageIndex, paths) in layer.pages) {
                for (path in paths) {
                    if (!filter.matches(layer.ownerDeviceId, pageIndex, path)) continue
                    if (path.strokeId.isNotEmpty() && !seenStrokeIds.add(path.strokeId)) continue
                    out.getOrPut(pageIndex) { mutableListOf() }.add(path)
                }
            }
        }
        return out.mapValues { it.value.toList() }
    }
}
