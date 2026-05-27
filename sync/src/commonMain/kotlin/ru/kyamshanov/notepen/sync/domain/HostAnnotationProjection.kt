package ru.kyamshanov.notepen.sync.domain

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.kyamshanov.notepen.annotation.domain.model.AnnotationFilter
import ru.kyamshanov.notepen.annotation.domain.model.AnnotationLayer
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.annotation.domain.model.EraserSettings
import ru.kyamshanov.notepen.annotation.domain.model.MarkerSettings
import ru.kyamshanov.notepen.annotation.domain.model.PageExtent
import ru.kyamshanov.notepen.annotation.domain.model.PenSettings
import ru.kyamshanov.notepen.annotation.domain.model.StickyHighlight
import ru.kyamshanov.notepen.annotation.domain.model.UnionAllPolicy
import ru.kyamshanov.notepen.annotation.domain.port.AnnotationRepository
import ru.kyamshanov.notepen.sync.domain.model.RectDto
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
    val pageExtents: Map<Int, PageExtent> = emptyMap(),
    val favoritePageIndices: Set<Int> = emptySet(),
    // Не синхронизируется дельтами (нет StrokeDelta для выделений) — переносится как есть,
    // чтобы headless-сохранение на хосте не затирало липкие выделения с диска.
    val highlights: Map<Int, List<StickyHighlight>> = emptyMap(),
)

private val logger = KotlinLogging.logger {}

/**
 * Debounce before flushing peer-originated edits to the host's on-disk file.
 * Маленькое значение: пользователь может переключиться на ПК и открыть документ
 * уже через секунду после рисования на планшете — диск должен быть свежим к
 * этому моменту, иначе ПК прочитает старую версию (без только что сделанной
 * заметки). Коалесинг сохраняется (повторные дельты перезапускают таймер).
 */
private const val FLUSH_DEBOUNCE_MS = 300L

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

    // Per-document debounce job for autonomous disk flush of peer-originated edits.
    private val flushJobs = mutableMapOf<String, Job>()

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
    fun follow(
        documentId: String,
        scope: CoroutineScope,
    ) {
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

    /**
     * Принимает дельту, пришедшую от пира, напрямую в проекцию — минуя
     * подписку на [SyncEngine.mergedDeltas].
     *
     * Это закрывает гонку: проекция начинала `follow` только при первом
     * `SaveRequest`/`AnnotationSnapshotRequest`, а движок не хранит штрихи и
     * эмитит их в `SharedFlow` без replay. Поэтому штрихи, нарисованные на
     * планшете до первого запроса, терялись — при сохранении на хост писался
     * лишь старый baseline с диска. Здесь baseline гарантированно загружается
     * до применения дельты, а вызовы упорядочены вызывающим (по одному на
     * сообщение из `incomingMessages`).
     *
     * No-op, если документа нет в опубликованном каталоге (чужой/неизвестный id).
     *
     * После применения планирует debounce-сброс на диск ([scope]), чтобы
     * правки с планшета попадали в файл хоста и были видны при следующем
     * открытии на ПК — даже если планшет не пришлёт явный `SaveRequest`.
     */
    suspend fun ingestPeerDelta(
        documentId: String,
        delta: StrokeDelta,
        scope: CoroutineScope,
    ) {
        ensureLoaded(documentId) ?: return
        apply(documentId, delta)
        scheduleFlush(documentId, scope)
    }

    /**
     * Гарантирует, что накопленное состояние [documentId] будет сброшено на диск
     * (debounce-коалесинг). Используется headless-обработчиком вместо прямой
     * записи на каждый `SaveRequest` — иначе при активном рисовании хост
     * перезаписывал весь файл десятки раз подряд. No-op для документа вне каталога.
     */
    suspend fun requestFlush(
        documentId: String,
        scope: CoroutineScope,
    ) {
        ensureLoaded(documentId) ?: return
        scheduleFlush(documentId, scope)
    }

    private suspend fun scheduleFlush(
        documentId: String,
        scope: CoroutineScope,
    ) {
        mutex.withLock {
            flushJobs[documentId]?.cancel()
            flushJobs[documentId] =
                scope.launch {
                    delay(FLUSH_DEBOUNCE_MS)
                    flushToDisk(documentId)
                }
        }
    }

    private suspend fun flushToDisk(documentId: String) {
        val hostUri = provider.resolveUri(documentId) ?: return
        val state = stateOf(documentId) ?: return
        val result =
            repository.save(
                pdfPath = hostUri,
                annotations = state.pages,
                scale = state.scale,
                pen = state.pen,
                marker = state.marker,
                eraser = state.eraser,
                currentPage = state.currentPage,
                currentPageOffset = state.currentPageOffset,
                favoritePageIndices = state.favoritePageIndices,
                pageExtents = state.pageExtents,
                highlights = state.highlights,
            )
        logger.info { "Projection autosave to disk: doc=$documentId success=${result.isSuccess}" }
    }

    private suspend fun ensureLoaded(documentId: String): Unit? {
        // Cheap fast path under lock.
        mutex.withLock { if (states.containsKey(documentId)) return Unit }
        val hostUri =
            provider.resolveUri(documentId) ?: run {
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
                state.favoritePageIndices = bundle.favoritePageIndices
                bundle.pages.forEach { (page, paths) ->
                    // Legacy on-disk strokes have no author → the reserved host layer.
                    state.layers
                        .getOrPut(AnnotationLayer.HOST) { linkedMapOf() }
                        .getOrPut(page) { mutableListOf() }
                        .addAll(paths)
                }
                bundle.pageExtents.forEach { (page, ext) ->
                    state.pageExtents[page] = ext
                }
                state.highlights = bundle.highlights
                logger.info {
                    "Projection loaded from disk: doc=$documentId pages=${bundle.pages.size}"
                }
            }
            states[documentId] = state
        }
        return Unit
    }

    private suspend fun apply(
        documentId: String,
        delta: StrokeDelta,
    ) {
        mutex.withLock {
            val state = states.getOrPut(documentId) { MutableDocumentState() }
            when (delta) {
                is StrokeDelta.Added -> {
                    val page =
                        state.layers
                            .getOrPut(delta.authorDeviceId) { linkedMapOf() }
                            .getOrPut(delta.pageIndex) { mutableListOf() }
                    if (page.none { it.strokeId == delta.strokeId }) {
                        page.add(delta.path.toDomain())
                    }
                    delta.pageExtent?.let { ext ->
                        val current = state.pageExtents[delta.pageIndex] ?: PageExtent.Pdf
                        state.pageExtents[delta.pageIndex] = current.union(ext.toDomain())
                    }
                }
                is StrokeDelta.Removed -> {
                    // The eraser (delta.authorDeviceId) may target another device's
                    // stroke, so remove the id from whichever layer holds it.
                    state.layers.values.forEach { layerPages ->
                        layerPages[delta.pageIndex]?.removeAll { it.strokeId == delta.strokeId }
                    }
                }
            }
        }
    }

    /**
     * Snapshot of every stroke currently held for [documentId], as wire-DTOs —
     * each carrying its real authoring device, so provenance survives the round
     * trip (peers' strokes are no longer relabelled as the host's).
     */
    suspend fun snapshotDtos(documentId: String): List<StrokeDelta.Added>? {
        ensureLoaded(documentId) ?: return null
        return mutex.withLock {
            val state = states[documentId] ?: return@withLock null
            val out = mutableListOf<StrokeDelta.Added>()
            val pageIndices =
                state.layers.values
                    .flatMap { it.keys }
                    .distinct()
                    .sorted()
            for (pageIndex in pageIndices) {
                // Page extent is document-level: attach it to the first stroke
                // emitted for the page; the receiver unions extents anyway.
                val extDto =
                    state.pageExtents[pageIndex]
                        ?.takeIf { it != PageExtent.Pdf }
                        ?.let { RectDto.fromDomain(it) }
                var attachedExtent = false
                for ((ownerDeviceId, layerPages) in state.layers) {
                    val paths = layerPages[pageIndex] ?: continue
                    for (path in paths) {
                        val id = path.strokeId.ifEmpty { "$ownerDeviceId#legacy-$pageIndex-${out.size}" }
                        val ext = if (!attachedExtent) extDto else null
                        if (ext != null) attachedExtent = true
                        out.add(
                            StrokeDelta.Added(
                                strokeId = id,
                                pageIndex = pageIndex,
                                authorDeviceId = ownerDeviceId,
                                clock = 0,
                                path = path.toDto(id),
                                pageExtent = ext,
                            ),
                        )
                    }
                }
            }
            out
        }
    }

    private class MutableDocumentState {
        /** Strokes per author device id (the layer key), then per page index. */
        val layers: MutableMap<String, MutableMap<Int, MutableList<DrawingPath>>> = linkedMapOf()
        val pageExtents: MutableMap<Int, PageExtent> = mutableMapOf()
        var scale: Int = 100
        var pen: PenSettings = PenSettings()
        var marker: MarkerSettings = MarkerSettings()
        var eraser: EraserSettings = EraserSettings()
        var currentPage: Int = 0
        var currentPageOffset: Int = 0
        var favoritePageIndices: Set<Int> = emptySet()
        var highlights: Map<Int, List<StickyHighlight>> = emptyMap()
    }

    private fun MutableDocumentState.toImmutable() =
        DocumentAnnotationState(
            // Default composition: union of all layers (= pre-layer behaviour).
            pages = UnionAllPolicy.compose(toAnnotationLayers(), AnnotationFilter.All),
            scale = scale,
            pen = pen,
            marker = marker,
            eraser = eraser,
            currentPage = currentPage,
            currentPageOffset = currentPageOffset,
            pageExtents = pageExtents.toMap(),
            favoritePageIndices = favoritePageIndices,
            highlights = highlights,
        )

    private fun MutableDocumentState.toAnnotationLayers(): List<AnnotationLayer> =
        layers.map { (deviceId, layerPages) ->
            AnnotationLayer(
                ownerDeviceId = deviceId,
                pages = layerPages.mapValues { it.value.toList() },
            )
        }
}
