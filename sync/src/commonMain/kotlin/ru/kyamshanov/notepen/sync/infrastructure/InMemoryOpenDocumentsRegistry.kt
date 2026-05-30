package ru.kyamshanov.notepen.sync.infrastructure

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.kyamshanov.notepen.sync.domain.model.OpenDocumentInfo
import ru.kyamshanov.notepen.sync.domain.port.OpenDocumentsProvider

/**
 * In-memory [OpenDocumentsProvider] that the editor publishes its currently
 * open tabs into. The catalog provider observes [openDocuments] to advertise
 * the set to peers and re-broadcast on change.
 */
class InMemoryOpenDocumentsRegistry : OpenDocumentsProvider {
    private val _openDocuments = MutableStateFlow<List<OpenDocumentInfo>>(emptyList())
    override val openDocuments: StateFlow<List<OpenDocumentInfo>> = _openDocuments.asStateFlow()

    /** Replaces the published open-document set. Called when editor tabs change. */
    fun publish(documents: List<OpenDocumentInfo>) {
        _openDocuments.value = documents
    }
}
