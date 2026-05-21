package ru.kyamshanov.notepen.sync.infrastructure

import ru.kyamshanov.notepen.mainscreen.domain.model.Folder
import ru.kyamshanov.notepen.mainscreen.domain.port.FolderRepository
import ru.kyamshanov.notepen.sync.domain.port.CatalogChangeNotifier

/**
 * Декоратор поверх [FolderRepository], который после каждой успешной мутации
 * дёргает [CatalogChangeNotifier.notifyChanged]. Чтение проксируется как есть.
 *
 * Размещён в `:sync`, чтобы не тащить sync-специфичные понятия в `:shared`.
 */
class NotifyingFolderRepository(
    private val delegate: FolderRepository,
    private val notifier: CatalogChangeNotifier,
) : FolderRepository {

    override suspend fun create(name: String, parentId: String?): Folder =
        delegate.create(name, parentId).also { notifier.notifyChanged() }

    override suspend fun delete(id: String) {
        delegate.delete(id)
        notifier.notifyChanged()
    }

    override suspend fun addFile(folderId: String, uri: String) {
        delegate.addFile(folderId, uri)
        notifier.notifyChanged()
    }

    override suspend fun removeFile(folderId: String, uri: String) {
        delegate.removeFile(folderId, uri)
        notifier.notifyChanged()
    }

    override suspend fun rename(id: String, newName: String) {
        delegate.rename(id, newName)
        notifier.notifyChanged()
    }

    override suspend fun getAll(): List<Folder> = delegate.getAll()

    override suspend fun getFilesInFolder(folderId: String): List<String> =
        delegate.getFilesInFolder(folderId)
}
