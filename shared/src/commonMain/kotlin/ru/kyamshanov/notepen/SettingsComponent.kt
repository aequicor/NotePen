package ru.kyamshanov.notepen

/**
 * Контракт компонента экрана настроек приложения.
 *
 * Объявляется в `:shared`, реализуется в `:common`. Позволяет [RootComponent]
 * ссылаться на компонент без циклической зависимости `:shared` → `:common`.
 */
interface SettingsComponent {
    /** Возврат на главный экран. */
    fun onBack()
}
