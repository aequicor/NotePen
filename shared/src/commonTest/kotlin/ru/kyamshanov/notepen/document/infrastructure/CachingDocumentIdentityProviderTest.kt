package ru.kyamshanov.notepen.document.infrastructure

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import ru.kyamshanov.notepen.document.domain.port.IdentityCache
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CachingDocumentIdentityProviderTest {
    private class CountingReader(
        private val content: ByteArray,
    ) : suspend (String) -> ByteArray {
        var reads = 0
            private set

        override suspend fun invoke(path: String): ByteArray {
            reads++
            return content
        }
    }

    private class RecordingCache : IdentityCache {
        val store = mutableMapOf<String, Pair<String?, String>>()
        var getCalls = 0
        var putCalls = 0

        override suspend fun get(
            path: String,
            fingerprint: String?,
        ): String? {
            getCalls++
            return store[path]?.takeIf { it.first == fingerprint }?.second
        }

        override suspend fun put(
            path: String,
            fingerprint: String?,
            sha256Hex: String,
        ) {
            putCalls++
            store[path] = fingerprint to sha256Hex
        }
    }

    @Test
    fun identityForPathIsContentAddressedAndWiredAsBasenameHash() =
        runTest {
            val reader = CountingReader("the content".encodeToByteArray())
            val provider =
                CachingDocumentIdentityProvider(
                    readBytes = reader,
                    fingerprint = { "fp" },
                    persistentCache = RecordingCache(),
                    ioDispatcher = Dispatchers.Unconfined,
                )

            val identity = provider.identityForPath("/docs/book.pdf")

            assertEquals(64, identity.canonicalId.hex.length)
            assertEquals("book.pdf#${identity.canonicalId.hex.take(16)}", identity.wireId)
        }

    @Test
    fun secondCallHitsInMemoryCacheAndDoesNotReHash() =
        runTest {
            val reader = CountingReader("payload".encodeToByteArray())
            val provider =
                CachingDocumentIdentityProvider(
                    readBytes = reader,
                    fingerprint = { "fp" },
                    persistentCache = RecordingCache(),
                    ioDispatcher = Dispatchers.Unconfined,
                )

            val first = provider.identityForPath("/docs/a.pdf")
            val second = provider.identityForPath("/docs/a.pdf")

            assertEquals(first, second)
            assertEquals(1, reader.reads, "content must be hashed exactly once")
        }

    @Test
    fun coldStartReusesPersistentCacheWithoutReHashing() =
        runTest {
            val cache = RecordingCache()
            cache.store["/docs/a.pdf"] = "fp" to "abc123"
            val reader = CountingReader("payload".encodeToByteArray())
            val provider =
                CachingDocumentIdentityProvider(
                    readBytes = reader,
                    fingerprint = { "fp" },
                    persistentCache = cache,
                    ioDispatcher = Dispatchers.Unconfined,
                )

            val identity = provider.identityForPath("/docs/a.pdf")

            assertEquals("abc123", identity.canonicalId.hex)
            assertEquals(0, reader.reads, "persistent cache hit must skip hashing")
        }

    @Test
    fun staleFingerprintForcesReHash() =
        runTest {
            val cache = RecordingCache()
            cache.store["/docs/a.pdf"] = "old-fp" to "stale-digest"
            val reader = CountingReader("new payload".encodeToByteArray())
            val provider =
                CachingDocumentIdentityProvider(
                    readBytes = reader,
                    fingerprint = { "new-fp" },
                    persistentCache = cache,
                    ioDispatcher = Dispatchers.Unconfined,
                )

            val identity = provider.identityForPath("/docs/a.pdf")

            assertEquals(1, reader.reads, "fingerprint change must invalidate the cached digest")
            assertTrue(identity.canonicalId.hex != "stale-digest")
        }

    @Test
    fun cachedWireIdForPathIsNullUntilWarmedThenReturnsWireId() =
        runTest {
            val reader = CountingReader("warm me".encodeToByteArray())
            val provider =
                CachingDocumentIdentityProvider(
                    readBytes = reader,
                    fingerprint = { "fp" },
                    persistentCache = RecordingCache(),
                    ioDispatcher = Dispatchers.Unconfined,
                )

            assertNull(provider.cachedWireIdForPath("/docs/b.pdf"))

            val identity = provider.identityForPath("/docs/b.pdf")

            assertEquals(identity.wireId, provider.cachedWireIdForPath("/docs/b.pdf"))
        }
}
