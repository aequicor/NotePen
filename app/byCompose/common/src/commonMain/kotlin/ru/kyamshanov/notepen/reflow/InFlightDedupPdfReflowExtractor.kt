package ru.kyamshanov.notepen.reflow

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.kyamshanov.notepen.reflow.api.PdfContentKind
import ru.kyamshanov.notepen.reflow.api.PdfReflowExtractor
import ru.kyamshanov.notepen.reflow.api.ReflowDocument
import kotlin.time.TimeSource

private val dedupLogger = KotlinLogging.logger {}

/**
 * Декоратор-кооператор для [PdfReflowExtractor]: схлопывает параллельные вызовы
 * [extract] на один и тот же путь в одну экстракцию. Когда reader-mode и фоновое
 * извлечение sticky-marker (см. `EditorPanel`) стартуют одновременно, без дедупа
 * PDFBox делает два полных прохода (на 238-страничном PDF на Huawei — по 80с
 * каждый, конкурируя за CPU и main-thread). Под этим декоратором leader-вызов
 * делает extract один раз, follower'ы получают тот же результат.
 *
 * Cancellation: все вызывающие разделяют судьбу leader-coroutine. Если leader
 * cancel'ится, follower'ы получат `CancellationException`; при ошибке — ту же
 * ошибку. Это стандартное поведение coalescing-декораторов.
 *
 * [probe] не дедупится — он дешёвый и идемпотентный.
 */
public class InFlightDedupPdfReflowExtractor(
    private val delegate: PdfReflowExtractor,
) : PdfReflowExtractor {
    private val mutex = Mutex()
    private val inflight = mutableMapOf<String, CompletableDeferred<ReflowDocument>>()

    override suspend fun probe(path: String): PdfContentKind = delegate.probe(path)

    override suspend fun extract(path: String): ReflowDocument {
        val (deferred, isLeader) =
            mutex.withLock {
                val existing = inflight[path]
                if (existing != null) {
                    dedupLogger.info { "PdfReflow: dedup follower path=${maskPath(path)}" }
                    existing to false
                } else {
                    val created = CompletableDeferred<ReflowDocument>()
                    inflight[path] = created
                    created to true
                }
            }
        if (isLeader) {
            try {
                val doc = delegate.extract(path)
                deferred.complete(doc)
            } catch (t: Throwable) {
                deferred.completeExceptionally(t)
                throw t
            } finally {
                mutex.withLock { if (inflight[path] === deferred) inflight.remove(path) }
            }
        }
        val mark = TimeSource.Monotonic.markNow()
        val result = deferred.await()
        if (!isLeader) {
            dedupLogger.info {
                "PdfReflow: dedup followed path=${maskPath(path)} wait=${mark.elapsedNow().inWholeMilliseconds}ms"
            }
        }
        return result
    }

    private fun maskPath(path: String): String = path.substringAfterLast('/')
}
