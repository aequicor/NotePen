package ru.kyamshanov.notepen.reflow

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import ru.kyamshanov.notepen.reflow.api.PdfContentKind
import ru.kyamshanov.notepen.reflow.api.PdfReflowExtractor
import ru.kyamshanov.notepen.reflow.api.ReflowDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class InFlightDedupPdfReflowExtractorTest {
    private class FakeExtractor : PdfReflowExtractor {
        val gates = mutableMapOf<String, CompletableDeferred<ReflowDocument>>()
        var extractCalls = 0
        var probeCalls = 0

        fun gate(path: String): CompletableDeferred<ReflowDocument> = gates.getOrPut(path) { CompletableDeferred() }

        override suspend fun probe(path: String): PdfContentKind {
            probeCalls++
            return PdfContentKind.TEXT_BASED
        }

        override suspend fun extract(path: String): ReflowDocument {
            extractCalls++
            return gate(path).await()
        }
    }

    @Test
    fun concurrentExtractsRunDelegateOnce() =
        runTest {
            val fake = FakeExtractor()
            val dedup = InFlightDedupPdfReflowExtractor(fake)
            val doc = ReflowDocument(kind = PdfContentKind.TEXT_BASED, blocks = emptyList())

            val first = async { dedup.extract("/a.pdf") }
            yield() // let leader register in the inflight map
            val second = async { dedup.extract("/a.pdf") }
            yield() // let follower observe the leader

            fake.gate("/a.pdf").complete(doc)
            assertSame(doc, first.await())
            assertSame(doc, second.await())
            assertEquals(1, fake.extractCalls)
        }

    @Test
    fun sequentialExtractsBothRun() =
        runTest {
            val fake = FakeExtractor()
            val dedup = InFlightDedupPdfReflowExtractor(fake)
            val doc1 = ReflowDocument(kind = PdfContentKind.TEXT_BASED, blocks = emptyList())
            val doc2 = ReflowDocument(kind = PdfContentKind.TEXT_BASED, blocks = emptyList())

            val first = async { dedup.extract("/a.pdf") }
            yield()
            fake.gate("/a.pdf").complete(doc1)
            assertSame(doc1, first.await())

            fake.gates.clear()
            val second = async { dedup.extract("/a.pdf") }
            yield()
            fake.gate("/a.pdf").complete(doc2)
            assertSame(doc2, second.await())
            assertEquals(2, fake.extractCalls)
        }

    @Test
    fun differentPathsDoNotShare() =
        runTest {
            val fake = FakeExtractor()
            val dedup = InFlightDedupPdfReflowExtractor(fake)
            val docA = ReflowDocument(kind = PdfContentKind.TEXT_BASED, blocks = emptyList())
            val docB = ReflowDocument(kind = PdfContentKind.TEXT_BASED, blocks = emptyList())

            val a = async { dedup.extract("/a.pdf") }
            val b = async { dedup.extract("/b.pdf") }
            yield()

            fake.gate("/a.pdf").complete(docA)
            fake.gate("/b.pdf").complete(docB)
            assertSame(docA, a.await())
            assertSame(docB, b.await())
            assertEquals(2, fake.extractCalls)
        }

    @Test
    fun probeIsNotDeduped() =
        runTest {
            val fake = FakeExtractor()
            val dedup = InFlightDedupPdfReflowExtractor(fake)

            dedup.probe("/a.pdf")
            dedup.probe("/a.pdf")
            assertEquals(2, fake.probeCalls)
        }

    @Test
    fun leaderFailurePropagatesToFollowers() =
        runTest {
            supervisorScope {
                val fake = FakeExtractor()
                val dedup = InFlightDedupPdfReflowExtractor(fake)

                val first = async { dedup.extract("/a.pdf") }
                yield()
                val second = async { dedup.extract("/a.pdf") }
                yield()

                val boom = RuntimeException("boom")
                fake.gate("/a.pdf").completeExceptionally(boom)

                val firstThrown = assertFailsWith<RuntimeException> { first.await() }
                val secondThrown = assertFailsWith<RuntimeException> { second.await() }
                assertTrue(firstThrown.message == "boom" && secondThrown.message == "boom")
            }
        }

    @Test
    fun afterFailureNextCallRunsDelegateAgain() =
        runTest {
            supervisorScope {
                val fake = FakeExtractor()
                val dedup = InFlightDedupPdfReflowExtractor(fake)

                val first = async { dedup.extract("/a.pdf") }
                yield()
                fake.gate("/a.pdf").completeExceptionally(RuntimeException("boom"))
                assertFailsWith<RuntimeException> { first.await() }

                fake.gates.clear()
                val doc = ReflowDocument(kind = PdfContentKind.TEXT_BASED, blocks = emptyList())
                val second = async { dedup.extract("/a.pdf") }
                yield()
                fake.gate("/a.pdf").complete(doc)
                assertSame(doc, second.await())
                assertEquals(2, fake.extractCalls)
            }
        }
}
