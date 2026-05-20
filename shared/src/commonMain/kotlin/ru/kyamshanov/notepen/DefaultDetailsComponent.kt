package ru.kyamshanov.notepen

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.DetailsComponent.Model
import ru.kyamshanov.notepen.mainscreen.domain.port.FileHistoryRepository

private val logger = KotlinLogging.logger {}

/**
 * Стандартная реализация [DetailsComponent].
 *
 * @param componentContext Контекст компонента Decompose.
 * @param title Путь/URI открытого файла (используется как заголовок и ключ истории).
 * @param historyRepository Порт для обновления lastPageIndex в истории.
 * @param onBackListener Обратный вызов для навигации назад.
 * @param onOpenLibraryListener Обратный вызов для открытия библиотеки поверх документа.
 * @param pendingTabUri Разделяемое значение «файл, выбранный в библиотеке, ждёт открытия вкладкой».
 * @param onPendingTabHandledListener Сброс [pendingTabUri] после открытия вкладки.
 */
class DefaultDetailsComponent(
    componentContext: ComponentContext,
    title: String,
    private val historyRepository: FileHistoryRepository,
    private val onBackListener: () -> Unit,
    private val onOpenLibraryListener: () -> Unit,
    override val pendingTabUri: Value<String>,
    private val onPendingTabHandledListener: () -> Unit,
) : DetailsComponent, ComponentContext by componentContext {

    private val scope = coroutineScope()

    private val uri: String = title

    override val model: Value<Model> =
        MutableValue(Model(title = title))

    override fun onBack() {
        onBackListener()
    }

    override fun openLibrary() {
        onOpenLibraryListener()
    }

    override fun onPendingTabHandled() {
        onPendingTabHandledListener()
    }

    /**
     * Сохраняет индекс страницы в историю. Best-effort: ошибки логируются, не пробрасываются.
     * Tracking: BL-14 / ADR-006.
     */
    override fun saveLastPageIndex(pageIndex: Int) {
        scope.launch {
            try {
                historyRepository.updateLastPage(uri, pageIndex)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Best-effort — не логируем uri (потенциально чувствительные данные)
                logger.warn { "updateLastPage failed: ${e::class.simpleName}" }
            }
        }
    }
}
