package ru.kyamshanov.notepen.reflow

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.reflow.api.PageBitmapProvider
import ru.kyamshanov.notepen.reflow.api.PdfContentKind
import ru.kyamshanov.notepen.reflow.api.PdfReflowExtractor
import ru.kyamshanov.notepen.reflow.api.ReflowDocument

private val decoratorLogger = KotlinLogging.logger {}

/**
 * Decorator над [PdfReflowExtractor], дающий дисковый кэш [ReflowDocument]:
 * первый extract идёт сквозь делегата (60+ секунд на 238-стр. PDF на mid-range
 * Android), результат пишется в [cache] асинхронно. Повторные extract'ы по тому
 * же пути возвращают документ из кэша за десятки миллисекунд.
 *
 * [probe] не кэшируется — он быстрый и идемпотентный.
 *
 * Запись идёт в [writeScope] (`SupervisorJob + Dispatchers.IO`), чтобы вызывающая
 * coroutine не ждала I/O после получения документа; ошибки записи проглатываются
 * и логируются — кэш необязателен.
 */
internal class DiskCachingPdfReflowExtractor(
    private val delegate: PdfReflowExtractor,
    private val cache: ReflowDocumentDiskCache,
    private val writeScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : PdfReflowExtractor {
    override suspend fun probe(path: String): PdfContentKind = delegate.probe(path)

    override suspend fun extract(path: String): ReflowDocument = readOrExtract(path) { delegate.extract(path) }

    /**
     * Кэш-семантика для [extractWithLattice]: hit → отдаём кэш (там УЖЕ может лежать
     * Lattice-уточнённый документ — формат сохраняет Table/Figure-структуру; флаги вроде
     * `wasTableFallback`/`Table.confidence` теряются, но не нужны при чтении). Miss →
     * прогоняем через делегата с Lattice и кэшируем результат. Так первая reader-mode
     * сессия пишет уточнённый документ, а последующие открытия мгновенно его поднимают.
     */
    override suspend fun extractWithLattice(
        path: String,
        pageBitmaps: PageBitmapProvider,
    ): ReflowDocument = readOrExtract(path) { delegate.extractWithLattice(path, pageBitmaps) }

    private suspend fun readOrExtract(
        path: String,
        actualExtract: suspend () -> ReflowDocument,
    ): ReflowDocument {
        val cached = cache.read(path)
        if (cached != null) return cached
        val doc = actualExtract()
        writeScope.launch {
            runCatching { cache.write(path, doc) }
                .onFailure { e -> decoratorLogger.warn(e) { "PdfReflow: cache write async failed" } }
        }
        return doc
    }
}
