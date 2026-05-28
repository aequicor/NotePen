package ru.kyamshanov.notepen.reflow

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import ru.kyamshanov.notepen.reflow.api.PdfContentKind
import ru.kyamshanov.notepen.reflow.api.PdfReflowExtractor
import ru.kyamshanov.notepen.reflow.api.ReflowDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
class DiskCachingPdfReflowExtractorTest {
    private class RecordingDelegate(
        private val doc: ReflowDocument,
    ) : PdfReflowExtractor {
        var extractCalls = 0
        var probeCalls = 0

        override suspend fun probe(path: String): PdfContentKind {
            probeCalls++
            return PdfContentKind.TEXT_BASED
        }

        override suspend fun extract(path: String): ReflowDocument {
            extractCalls++
            return doc
        }
    }

    private class InMemoryCache : ReflowDocumentDiskCache {
        val entries = mutableMapOf<String, ReflowDocument>()
        var reads = 0
        var writes = 0

        override suspend fun read(path: String): ReflowDocument? {
            reads++
            return entries[path]
        }

        override suspend fun write(
            path: String,
            document: ReflowDocument,
        ) {
            writes++
            entries[path] = document
        }
    }

    @Test
    fun firstExtractCallsDelegateAndWritesCache() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val doc = ReflowDocument(kind = PdfContentKind.TEXT_BASED, blocks = emptyList())
            val delegate = RecordingDelegate(doc)
            val cache = InMemoryCache()
            val decorator =
                DiskCachingPdfReflowExtractor(
                    delegate = delegate,
                    cache = cache,
                    writeScope = CoroutineScope(dispatcher),
                )
            assertSame(doc, decorator.extract("/a.pdf"))
            testScheduler.advanceUntilIdle()
            assertEquals(1, delegate.extractCalls)
            assertEquals(1, cache.reads)
            assertEquals(1, cache.writes)
            assertEquals(doc, cache.entries["/a.pdf"])
        }

    @Test
    fun cachedExtractSkipsDelegate() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val doc = ReflowDocument(kind = PdfContentKind.TEXT_BASED, blocks = emptyList())
            val delegate = RecordingDelegate(doc)
            val cache =
                InMemoryCache().apply { entries["/a.pdf"] = doc }
            val decorator =
                DiskCachingPdfReflowExtractor(
                    delegate = delegate,
                    cache = cache,
                    writeScope = CoroutineScope(dispatcher),
                )
            assertSame(doc, decorator.extract("/a.pdf"))
            testScheduler.advanceUntilIdle()
            assertEquals(0, delegate.extractCalls)
            assertEquals(0, cache.writes)
        }

    @Test
    fun probePassesThroughWithoutCacheReads() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val doc = ReflowDocument(kind = PdfContentKind.TEXT_BASED, blocks = emptyList())
            val delegate = RecordingDelegate(doc)
            val cache = InMemoryCache()
            val decorator =
                DiskCachingPdfReflowExtractor(
                    delegate = delegate,
                    cache = cache,
                    writeScope = CoroutineScope(dispatcher),
                )
            decorator.probe("/a.pdf")
            decorator.probe("/a.pdf")
            assertEquals(2, delegate.probeCalls)
            assertEquals(0, cache.reads)
        }
}
