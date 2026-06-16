package ru.kyamshanov.notepen.uikitsandbox.domain.model

/**
 * Recoverable-ошибки домена sandbox-виджетов.
 */
internal sealed interface SandboxWidgetFailure {
    /** Ошибка удалённого источника или сетевого сценария обновления. */
    data object Network : SandboxWidgetFailure

    /** Ошибка локального хранения состояния виджетов. */
    data object Storage : SandboxWidgetFailure

    /**
     * Неожиданная ошибка, которую можно безопасно показать через message.
     *
     * @property message готовое описание ошибки для UI-события.
     */
    data class Unknown(
        val message: String,
    ) : SandboxWidgetFailure
}

/**
 * Результат операций доменного слоя без проброса recoverable-исключений наружу.
 *
 * @param T тип успешного значения.
 */
internal sealed interface SandboxOutcome<out T> {
    /**
     * Успешное завершение операции.
     *
     * @property value значение, возвращённое операцией.
     */
    data class Success<T>(
        val value: T,
    ) : SandboxOutcome<T>

    /**
     * Recoverable-ошибка операции.
     *
     * @property reason типизированная причина ошибки.
     */
    data class Failure(
        val reason: SandboxWidgetFailure,
    ) : SandboxOutcome<Nothing>
}
