package ru.kyamshanov.notepen.uikitsandbox.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

/**
 * Удалённый data source sandbox-виджетов.
 *
 * Контракт показывает Ktor-образную границу data-слоя без зависимости presentation
 * или domain-кода от HTTP.
 */
internal interface SandboxWidgetRemoteDataSource {
    /**
     * Загружает список виджетов из внешнего источника.
     *
     * @return DTO удалённых виджетов.
     * @throws Throwable если источник завершился ошибкой.
     */
    suspend fun fetchWidgets(): List<SandboxWidgetDto>
}

/**
 * Ktor-реализация remote data source для архитектурного примера.
 *
 * @property httpClient клиент, которым выполняется GET-запрос.
 * @property endpointUrl адрес endpoint-а со списком DTO.
 */
internal class KtorSandboxWidgetRemoteDataSource(
    private val httpClient: HttpClient,
    private val endpointUrl: String,
) : SandboxWidgetRemoteDataSource {
    /**
     * Выполняет GET-запрос и декодирует тело ответа.
     *
     * @return список DTO из ответа.
     */
    override suspend fun fetchWidgets(): List<SandboxWidgetDto> = httpClient.get(endpointUrl).body()
}

/**
 * Статическая remote-реализация для sandbox-запуска без сети.
 *
 * @property widgets список DTO, который будет возвращаться при каждом запросе.
 */
internal class StaticSandboxWidgetRemoteDataSource(
    private val widgets: List<SandboxWidgetDto> = emptyList(),
) : SandboxWidgetRemoteDataSource {
    /**
     * Возвращает заранее заданный список DTO.
     *
     * @return [widgets].
     */
    override suspend fun fetchWidgets(): List<SandboxWidgetDto> = widgets
}
