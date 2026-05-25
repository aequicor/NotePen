package ru.kyamshanov.notepen.sync.infrastructure

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import ru.kyamshanov.notepen.sync.domain.port.LocalDocumentIdRegistry

private val logger = KotlinLogging.logger {}

/**
 * Persistent [LocalDocumentIdRegistry] поверх крошечного JSON-манифеста
 * `<receivedDir>/.notepen-doc-ids.json`. Подгружается синхронно при создании,
 * поэтому `lookup` сразу видит данные с предыдущего запуска.
 *
 * Запись — async, через `withContext(ioDispatcher)`: серьёзной нагрузки нет
 * (несколько KB файл), но не блокируем UI на flush'е.
 */
class JsonLocalDocumentIdRegistry(
    private val manifestPath: String,
    private val ioDispatcher: CoroutineDispatcher,
) : LocalDocumentIdRegistry {
    private val mutex = Mutex()
    private val cache = MutableStateFlow<Map<String, String>>(emptyMap())
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), String.serializer())

    init {
        if (okio_exists(manifestPath)) {
            val loaded =
                runCatching {
                    val bytes = okio_readBytes(manifestPath)
                    json.decodeFromString(serializer, bytes.decodeToString())
                }.getOrElse {
                    logger.warn { "Failed to read $manifestPath: ${it::class.simpleName}; starting empty" }
                    emptyMap()
                }
            cache.value = loaded
        }
    }

    override fun lookup(localPath: String): String? = cache.value[localPath]

    override suspend fun register(
        localPath: String,
        documentId: String,
    ) {
        val updated: Map<String, String>
        mutex.withLock {
            cache.update { it + (localPath to documentId) }
            updated = cache.value
        }
        persist(updated)
    }

    override suspend fun forget(localPath: String) {
        val updated: Map<String, String>
        mutex.withLock {
            cache.update { it - localPath }
            updated = cache.value
        }
        persist(updated)
    }

    private suspend fun persist(snapshot: Map<String, String>) {
        withContext(ioDispatcher) {
            runCatching {
                val text = json.encodeToString(serializer, snapshot)
                okio_writeBytes(manifestPath, text.encodeToByteArray())
            }.onFailure { logger.warn { "Failed to write $manifestPath: ${it::class.simpleName}" } }
        }
    }
}
