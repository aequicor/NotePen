package ru.kyamshanov.notepen.sync.domain

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.annotation.domain.model.EraserSettings
import ru.kyamshanov.notepen.annotation.domain.model.MarkerSettings
import ru.kyamshanov.notepen.annotation.domain.model.PenSettings
import ru.kyamshanov.notepen.annotation.domain.port.AnnotationRepository
import ru.kyamshanov.notepen.sync.domain.model.StrokeDelta
import ru.kyamshanov.notepen.sync.domain.model.toDomain
import ru.kyamshanov.notepen.sync.domain.model.toDto

/** Per-document accumulated annotation state living on the host. */
data class DocumentAnnotationState(
    val pages: Map<Int, List<DrawingPath>>,
    val scale: Int,
    val pen: PenSettings,
    val marker: MarkerSettings,
    val eraser: EraserSettings,
    val currentPage: Int,
    val currentPageOffset: Int,
)

private val logger = KotlinLogging.logger {}

/**
 * Host-side projection that mirrors every `documentId`'s stroke state in RAM,
 * driven by [SyncEngine.mergedDeltas].
 *
 * The host can serve [ru.kyamshanov.notepen.sync.domain.model.NetworkMessage.SaveRequest]
 * and [ru.kyamshanov.notepen.sync.domain.model.NetworkMessage.AnnotationSnapshotRequest]
 * without opening a [`DetailsContent`][ru.kyamshanov.notepen] for the document
 * — closing the long-standing "headless save" gap from Phase 3.
 *
 * Lifecycle:
 * - First time the projection encounters a [documentId], it lazily loads the
 *   existing [AnnotationBundle] from disk (via [AnnotationRepository.load] with
 *   the host URI resolved by [provider]). This means edits made before the
 *   peer started sending deltas are honoured.
 * - Subsequent deltas (peer-originated AND local — [SyncEngine.applyLocal]
 *   mirrors local edits onto `mergedDeltas`) update the cached state by LWW.
 *
 * Concurrency: a single [Mutex] guards both the projection map and the lazy
 * loader. The expected document count is low (≤ a few dozen recents) so
 * coarse locking is fine.
 */
class HostAnnotationProjection(
    private val registry: SyncEngineRegistry,
    private val provider: RemoteCatalogProvider,
    private val repository: AnnotationRepository,
) {

    private val mutex = Mutex()
    private val states = mutableMapOf<String, MutableDocumentState>()
    private val followedEngines = mutableSetOf<String>()

    /**
     * Returns the current accumulated state for [documentId], lazily loading
     * from disk on the first request. Returns `null` if the document is not
     * in the published catalog.
     */
    suspend fun stateOf(documentId: String): DocumentAnnotationState? {
        ensureLoaded(documentId) ?: return null
        return mutex.withLock { states[documentId]?.toImmutable() }
    }

    /**
     * Subscribes to the engine for [documentId] (creating it if needed) and
     * pipes its `mergedDeltas` into the projection. Safe to call repeatedly;
     * second and later calls are no-ops.
     */
    fun follow(documentId: String, scope: CoroutineScope) {
        scope.launch {
            val alreadyFollowed = mutex.withLock { !followedEngines.add(documentId) }
            if (alreadyFollowed) return@launch
            // Make sure baseline state is loaded before deltas start arriving;
            // otherwise the first Added would clobber on-disk strokes.
            ensureLoaded(documentId)
            val engine = registry.get(documentId)
            engine.mergedDeltas.collect { delta -> apply(documentId, delta) }
        }
    }

    private suspend fun ensureLoaded(documentId: String): Unit? {
        // Cheap fast path under lock.
        mutex.withLock { if (states.containsKey(documentId)) return Unit }
        val hostUri = provider.resolveUri(documentId) ?: run {
            logger.debug { "ensureLoaded($documentId) — not in catalog, projection skipped" }
            return null
        }
        val bundle = runCatching { repository.load(hostUri).getOrNull() }.getOrNull()
        mutex.withLock {
            if (states.containsKey(documentId)) return Unit
            val state = MutableDocumentState()
            if (bundle != null) {
                state.scale = bundle.scale
                state.pen = bundle.pen
                state.marker = bundle.marker
                state.eraser = bundle.eraser
                state.currentPage = bundle.currentPage
                state.currentPageOffset = bundle.currentPageOffset
                bundle.pages.forEach { (page, paths) ->
                    state.pages.getOrPut(page) { mutableListOf() }.addAll(paths)
                }
                logger.info {
                    "Projection loaded from disk: doc=$documentId pages=${bundle.pages.size}"
                }
            }
            states[documentId] = state
        }
        return Unit
    }

    private suspend fun apply(documentId: String, delta: StrokeDelta) {
        mutex.withLock {
            val state = states.getOrPut(documentId) { MutableDocumentState() }
            val page = state.pages.getOrPut(delta.pageIndex) { mutableListOf() }
            when (delta) {
                is StrokeDelta.Added -> {
                    if (page.none { it.strokeId == delta.strokeId }) {
                        page.add(delta.path.toDomain())
                    }
                }
                is StrokeDelta.Removed -> {
                    page.removeAll { it.strokeId == delta.strokeId }
                }
            }
        }
    }

    /** Snapshot of every stroke currently held for [documentId], as wire-DTOs. */
    suspend fun snapshotDtos(documentId: String): List<StrokeDelta.Added>? {
        val s = stateOf(documentId) ?: return null
        val deviceId = "host"
        val out = mutableListOf<StrokeDelta.Added>()
        for ((pageIndex, paths) in s.pages) {
            for (path in paths) {
                val id = path.strokeId.ifEmpty { "$deviceId#legacy-$pageIndex-${out.size}" }
                out.add(
                    StrokeDelta.Added(
                        strokeId = id,
                        pageIndex = pageIndex,
                        authorDeviceId = deviceId,
                        clock = 0,
                        path = path.toDto(id),
                    ),
                )
            }
        }
        return out
    }

    private class MutableDocumentState {
        val pages: MutableMap<Int, MutableList<DrawingPath>> = mutableMapOf()
        var scale: Int = 100
        var pen: PenSettings = PenSettings()
        var marker: MarkerSettings = MarkerSettings()
        var eraser: EraserSettings = EraserSettings()
        var currentPage: Int = 0
        var currentPageOffset: Int = 0
    }

    private fun MutableDocumentState.toImmutable() = DocumentAnnotationState(
        pages = pages.mapValues { it.value.toList() },
        scale = scale,
        pen = pen,
        marker = marker,
        eraser = eraser,
        currentPage = currentPage,
        currentPageOffset = currentPageOffset,
    )
}
