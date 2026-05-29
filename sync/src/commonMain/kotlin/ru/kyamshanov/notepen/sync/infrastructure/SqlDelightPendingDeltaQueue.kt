package ru.kyamshanov.notepen.sync.infrastructure

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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
 *
 * Lazy open: [databaseProvider] is invoked on first query, not at construction.
 * Opening a SQLite database (native lib extraction + first JDBC connection) can
 * cost seconds on a cold start; deferring it keeps that work off the caller's
 * thread (it runs on [ioDispatcher] via the suspend methods and [pendingCounts]).
 */
class SqlDelightPendingDeltaQueue(
    databaseProvider: () -> NotePenSyncDatabase,
    private val ioDispatcher: CoroutineDispatcher,
    private val clock: () -> Long = { currentTimeMillis() },
) : PendingDeltaQueue {
    private val database by lazy { databaseProvider() }
    private val queries by lazy { database.pendingDeltaQueries }
    private val migrationQueries by lazy { database.migrationMarkerQueries }
    private val json = Json { classDiscriminator = "type" }

    /**
     * Runs the one-time data migration for the content-addressed documentId
     * change (M0 IDENTITY SPINE): clears the un-broadcast delta outbox exactly
     * once, because every queued delta is keyed by the *old* path-FNV-1a
     * documentId and would never match the new `sha256`-of-content wire id on
     * replay. Annotations themselves are unaffected — they live in path-keyed
     * sidecars; only this outbox is reset, and a subsequent re-open re-syncs via
     * last-writer-wins.
     *
     * Idempotent: guarded by a [MigrationMarker] row, so it runs on the first
     * launch after the change and never again. Forces the lazy DB open, so the
     * caller should invoke it on a background dispatcher (it already hops to
     * [ioDispatcher]).
     */
    suspend fun runContentAddressedIdMigration() {
        withContext(ioDispatcher) {
            val alreadyApplied =
                migrationQueries.isApplied(CONTENT_ADDRESSED_ID_MIGRATION_KEY).executeAsOne() > 0
            if (alreadyApplied) return@withContext
            queries.clearAll()
            migrationQueries.markApplied(CONTENT_ADDRESSED_ID_MIGRATION_KEY, clock())
        }
    }

    override suspend fun enqueue(
        documentId: String,
        delta: StrokeDelta,
    ) {
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

    override suspend fun markSent(
        documentId: String,
        upToClock: Long,
    ) {
        withContext(ioDispatcher) {
            queries.deleteUpToClock(document_id = documentId, clock = upToClock)
        }
    }

    override suspend fun pendingCount(documentId: String): Int =
        withContext(ioDispatcher) { queries.countByDocument(documentId).executeAsOne().toInt() }

    // flow {} + flowOn defer the first `queries` access (which forces the lazy
    // DB open) onto [ioDispatcher] at collection time — calling this function
    // never opens the database on the caller's thread.
    override fun pendingCounts(): Flow<Map<String, Int>> =
        flow {
            emitAll(
                queries
                    .countsAll()
                    .asFlow()
                    .mapToList(ioDispatcher)
                    .map { rows -> rows.associate { it.document_id to it.pendingCount.toInt() } },
            )
        }.flowOn(ioDispatcher)
}

/**
 * [MigrationMarker] key for the one-time pending-delta reset triggered by the
 * content-addressed documentId change. Bump (new key) only if another one-time
 * reset is ever needed; never reuse this value.
 */
private const val CONTENT_ADDRESSED_ID_MIGRATION_KEY = "pending_delta_reset_content_addressed_id_v1"

/** Platform-provided wall clock in epoch millis. */
internal expect fun currentTimeMillis(): Long
