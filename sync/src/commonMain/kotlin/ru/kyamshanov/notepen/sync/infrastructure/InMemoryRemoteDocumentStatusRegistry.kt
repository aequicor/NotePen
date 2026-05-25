package ru.kyamshanov.notepen.sync.infrastructure

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ru.kyamshanov.notepen.sync.domain.model.SyncStatus
import ru.kyamshanov.notepen.sync.domain.port.RemoteDocumentStatusRegistry

/**
 * In-memory [RemoteDocumentStatusRegistry] for Phase 6.
 *
 * Survives reconnects within one app session; lost on process death. When
 * the queue gains SQLDelight persistence the orphan flag should follow into
 * the same table (so the user still sees the warning after restart).
 */
class InMemoryRemoteDocumentStatusRegistry : RemoteDocumentStatusRegistry {
    private val _statuses = MutableStateFlow<Map<String, SyncStatus>>(emptyMap())
    override val statuses: StateFlow<Map<String, SyncStatus>> = _statuses.asStateFlow()

    override suspend fun set(
        documentId: String,
        status: SyncStatus,
    ) {
        _statuses.update { current ->
            when (status) {
                SyncStatus.Synced -> if (current.containsKey(documentId)) current - documentId else current
                else -> current + (documentId to status)
            }
        }
    }
}
