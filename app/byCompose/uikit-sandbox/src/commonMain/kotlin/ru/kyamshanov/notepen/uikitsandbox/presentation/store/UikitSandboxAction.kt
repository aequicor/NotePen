package ru.kyamshanov.notepen.uikitsandbox.presentation.store

/**
 * Bootstrap-действия MVIKotlin Store sandbox-фичи.
 *
 * Action отличается от intent тем, что создаётся Store bootstrapper-ом, а не UI.
 * Executor получает action при создании Store и запускает начальную side-effect
 * последовательность.
 */
internal sealed interface UikitSandboxAction {
    /**
     * Запустить начальное наблюдение и refresh.
     *
     * Executor должен сначала подписаться на локальный список, затем инициировать
     * refresh, чтобы UI увидел seed-данные до завершения remote-обновления.
     */
    data object Start : UikitSandboxAction
}
