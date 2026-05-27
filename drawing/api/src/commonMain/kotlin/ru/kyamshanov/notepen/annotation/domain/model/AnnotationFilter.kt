package ru.kyamshanov.notepen.annotation.domain.model

/**
 * Predicate selecting which strokes a [MergePolicy] keeps when composing
 * [AnnotationLayer]s into a single view.
 *
 * The set is modelled in full so callers can express rich queries — by author,
 * tool, page, time, and boolean combinations — but the only variant exercised in
 * production today is [All]. See [matches] for what each variant currently
 * evaluates against.
 */
sealed interface AnnotationFilter {
    /** Keeps every stroke. */
    data object All : AnnotationFilter

    /** Keeps strokes authored by any of these device ids ([AnnotationLayer.ownerDeviceId]). */
    data class ByAuthor(
        val deviceIds: Set<String>,
    ) : AnnotationFilter

    /** Keeps strokes produced by any of these tools. */
    data class ByTool(
        val tools: Set<ToolKind>,
    ) : AnnotationFilter

    /** Keeps strokes on a page within this (inclusive) range. */
    data class ByPageRange(
        val pages: IntRange,
    ) : AnnotationFilter

    /**
     * Keeps strokes authored within `[fromMillis, toMillis)`.
     *
     * Modelled for forthcoming annotation-review features. Per-stroke timestamps
     * are not captured yet, so this currently keeps every stroke (pass-through);
     * it gains effect once strokes carry a creation time.
     */
    data class ByTime(
        val fromMillis: Long,
        val toMillis: Long,
    ) : AnnotationFilter

    /** Keeps a stroke only if every one of [filters] keeps it. */
    data class And(
        val filters: List<AnnotationFilter>,
    ) : AnnotationFilter

    /** Keeps a stroke if any of [filters] keeps it. */
    data class Or(
        val filters: List<AnnotationFilter>,
    ) : AnnotationFilter

    /** Inverts [filter]. */
    data class Not(
        val filter: AnnotationFilter,
    ) : AnnotationFilter
}

/**
 * Evaluates this filter for a single stroke in context.
 *
 * @param ownerDeviceId the [AnnotationLayer] the stroke belongs to.
 * @param pageIndex zero-based page the stroke is on.
 * @param path the stroke itself (for tool-based filtering).
 */
fun AnnotationFilter.matches(
    ownerDeviceId: String,
    pageIndex: Int,
    path: DrawingPath,
): Boolean =
    when (this) {
        AnnotationFilter.All -> true
        is AnnotationFilter.ByAuthor -> ownerDeviceId in deviceIds
        is AnnotationFilter.ByTool -> path.toolType in tools
        is AnnotationFilter.ByPageRange -> pageIndex in pages
        // No per-stroke timestamp is captured yet, so no time constraint applies.
        is AnnotationFilter.ByTime -> true
        is AnnotationFilter.And -> filters.all { it.matches(ownerDeviceId, pageIndex, path) }
        is AnnotationFilter.Or -> filters.any { it.matches(ownerDeviceId, pageIndex, path) }
        is AnnotationFilter.Not -> !filter.matches(ownerDeviceId, pageIndex, path)
    }
