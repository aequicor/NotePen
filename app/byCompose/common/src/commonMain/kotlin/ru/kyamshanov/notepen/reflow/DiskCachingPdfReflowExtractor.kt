package ru.kyamshanov.notepen.reflow

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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

    override suspend fun extract(path: String): ReflowDocument {
        val cached = cache.read(path)
        if (cached != null) return cached
        val doc = delegate.extract(path)
        writeScope.launch {
            runCatching { cache.write(path, doc) }
                .onFailure { e -> decoratorLogger.warn(e) { "PdfReflow: cache write async failed" } }
        }
        return doc
    }
}
