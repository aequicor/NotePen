package ru.kyamshanov.notepen.sync.domain.port

import kotlinx.coroutines.flow.Flow

/**
 * Шина уведомлений об изменениях локального каталога (история файлов + папки).
 *
 * Любая мутация (`upsert`, `create`, `delete`, `rename`, `addFile`, `removeFile`,
 * `updateStatus`, `updateLastPage`) должна закончиться вызовом [notifyChanged].
 * `RemoteCatalogProvider` подписывается на [changes] и рассылает подключённым
 * пирам `NetworkMessage.RemoteCatalogChanged`, чтобы они подтянули свежий
 * снапшот.
 *
 * Эмиссии «склеиваются»: достаточно одного сигнала между двумя broadcast'ами;
 * подписчику не важно, сколько мутаций произошло — он всё равно перечитает
 * каталог целиком.
 */
interface CatalogChangeNotifier {

    /** Поток сигналов «каталог изменился». Подписка горячая. */
    val changes: Flow<Unit>

    /** Сообщает шине об изменении локального каталога. */
    fun notifyChanged()
}
