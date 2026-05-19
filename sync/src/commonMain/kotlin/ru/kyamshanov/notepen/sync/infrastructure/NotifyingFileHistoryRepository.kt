package ru.kyamshanov.notepen.sync.infrastructure

import ru.kyamshanov.notepen.mainscreen.domain.model.AvailabilityStatus
import ru.kyamshanov.notepen.mainscreen.domain.model.RecentFile
import ru.kyamshanov.notepen.mainscreen.domain.port.FileHistoryRepository
import ru.kyamshanov.notepen.sync.domain.port.CatalogChangeNotifier

/**
 * Декоратор поверх [FileHistoryRepository], дёргающий [CatalogChangeNotifier]
 * после каждой успешной мутации. Чтение проксируется без изменений.
 */
class NotifyingFileHistoryRepository(
    private val delegate: FileHistoryRepository,
    private val notifier: CatalogChangeNotifier,
) : FileHistoryRepository {

    override suspend fun getAll(): List<RecentFile> = delegate.getAll()

    override suspend fun upsert(file: RecentFile, lastPageIndex: Int) {
        delegate.upsert(file, lastPageIndex)
        notifier.notifyChanged()
    }

    override suspend fun updateStatus(id: String, status: AvailabilityStatus) {
        delegate.updateStatus(id, status)
        notifier.notifyChanged()
    }

    override suspend fun updateLastPage(uri: String, pageIndex: Int) {
        // Индекс текущей страницы не входит в RemoteCatalog и зовётся на каждый
        // скролл — сигнал тут привёл бы к лавине broadcast'ов без пользы.
        delegate.updateLastPage(uri, pageIndex)
    }

    override suspend fun rollbackUpsert(uri: String) {
        delegate.rollbackUpsert(uri)
        notifier.notifyChanged()
    }
}
