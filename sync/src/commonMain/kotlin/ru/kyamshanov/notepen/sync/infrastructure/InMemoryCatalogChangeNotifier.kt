package ru.kyamshanov.notepen.sync.infrastructure

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import ru.kyamshanov.notepen.sync.domain.port.CatalogChangeNotifier

/**
 * Hot in-memory реализация [CatalogChangeNotifier]. Без буфера replay'я; при
 * переполнении (нет активного подписчика) — самый старый сигнал отбрасывается,
 * поскольку семантика «каталог изменился» идемпотентна.
 */
class InMemoryCatalogChangeNotifier : CatalogChangeNotifier {
    private val _changes =
        MutableSharedFlow<Unit>(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    override val changes: Flow<Unit> = _changes.asSharedFlow()

    override fun notifyChanged() {
        _changes.tryEmit(Unit)
    }
}
