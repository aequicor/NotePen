package ru.kyamshanov.notepen.sync.infrastructure

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import ru.kyamshanov.notepen.sync.db.NotePenSyncDatabase
import ru.kyamshanov.notepen.sync.domain.model.StrokeDelta
import ru.kyamshanov.notepen.sync.domain.port.PendingDeltaQueue

/**
 * SQLDelight-backed [PendingDeltaQueue] that survives process restarts.
 *
 * Replaces [InMemoryPendingDeltaQueue] for the production wire-up — once a
 * user goes offline and closes the app, their unsent deltas are reloaded
 * from disk on next launch and replayed on the next reconnect.
 *
 * The schema lives in `PendingDelta.sq`; generated symbols are in
 * `ru.kyamshanov.notepen.sync.db.*`.
 *
 * Concurrency: SQLDelight queries are blocking; all writes hop to [ioDispatcher].
 * Reads exposed as suspends do the same for symmetry.
 */
class SqlDelightPendingDeltaQueue(
    database: NotePenSyncDatabase,
    private val ioDispatcher: CoroutineDispatcher,
    private val clock: () -> Long = { currentTimeMillis() },
) : PendingDeltaQueue {

    private val queries = database.pendingDeltaQueries
    private val json = Json { classDiscriminator = "type" }

    override suspend fun enqueue(documentId: String, delta: StrokeDelta) {
        val payload = json.encodeToString(StrokeDelta.serializer(), delta)
        withContext(ioDispatcher) {
            queries.enqueue(
                document_id = documentId,
                clock = delta.clock,
                payload_json = payload,
                queued_at = clock(),
            )
        }
    }

    override suspend fun peek(documentId: String): List<StrokeDelta> =
        withContext(ioDispatcher) {
            queries.peekByDocument(documentId).executeAsList().map {
                json.decodeFromString(StrokeDelta.serializer(), it)
            }
        }

    override suspend fun markSent(documentId: String, upToClock: Long) {
        withContext(ioDispatcher) {
            queries.deleteUpToClock(document_id = documentId, clock = upToClock)
        }
    }

    override suspend fun pendingCount(documentId: String): Int =
        withContext(ioDispatcher) { queries.countByDocument(documentId).executeAsOne().toInt() }

    override fun pendingCounts(): Flow<Map<String, Int>> =
        queries.countsAll().asFlow()
            .mapToList(ioDispatcher)
            .map { rows -> rows.associate { it.document_id to it.pendingCount.toInt() } }
}

/** Platform-provided wall clock in epoch millis. */
internal expect fun currentTimeMillis(): Long
