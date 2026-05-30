package ru.kyamshanov.notepen.sync.domain.port

import kotlinx.coroutines.flow.StateFlow
import ru.kyamshanov.notepen.sync.domain.model.OpenDocumentInfo

/**
 * Source of the documents currently open in this device's editor tabs, so the
 * host can advertise them to peers in
 * [ru.kyamshanov.notepen.sync.domain.model.RemoteCatalog.openDocuments].
 *
 * The editor publishes its open tabs here; [RemoteCatalogProvider] reads
 * [openDocuments] when building a snapshot and re-broadcasts the catalog when
 * the set changes, so a peer's «open on the other device» list stays live.
 */
interface OpenDocumentsProvider {
    /** The documents currently open in active tabs. Empty when nothing is open. */
    val openDocuments: StateFlow<List<OpenDocumentInfo>>
}
