package ru.kyamshanov.notepen.sync.infrastructure

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ru.kyamshanov.notepen.sync.domain.port.OpenDocumentRegistry

/**
 * In-memory реализация [OpenDocumentRegistry]. acquire/release ожидаются с
 * главного потока (Compose lifecycle), поэтому ref-count не защищён мьютексом —
 * `MutableStateFlow.update` обеспечивает атомарность набора открытых id, а
 * сама карта счётчиков мутируется однопоточно.
 */
class InMemoryOpenDocumentRegistry : OpenDocumentRegistry {

    private val refCounts = mutableMapOf<String, Int>()
    private val _openDocumentIds = MutableStateFlow<Set<String>>(emptySet())

    override val openDocumentIds: StateFlow<Set<String>> = _openDocumentIds.asStateFlow()

    override fun acquire(documentId: String) {
        if (documentId.isBlank()) return
        val current = refCounts.getOrElse(documentId) { 0 }
        refCounts[documentId] = current + 1
        if (current == 0) {
            _openDocumentIds.update { it + documentId }
        }
    }

    override fun release(documentId: String) {
        if (documentId.isBlank()) return
        val current = refCounts.getOrElse(documentId) { 0 }
        if (current <= 1) {
            refCounts.remove(documentId)
            _openDocumentIds.update { it - documentId }
        } else {
            refCounts[documentId] = current - 1
        }
    }
}
