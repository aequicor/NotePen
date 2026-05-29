package ru.kyamshanov.notepen

/**
 * Контракт компонента экрана управления подключёнными библиотеками
 * («Источники библиотек»).
 *
 * Объявляется в `:shared`, реализуется в `:common` через
 * `ru.kyamshanov.notepen.library.ui.LibrarySourcesComponentImpl` — это позволяет
 * [RootComponent] ссылаться на компонент без циклической зависимости
 * `:shared` → `:common` (и без зависимости `:shared` → `:library`). Состояние и
 * действия (список библиотек, подключение/отключение, тумблер старта, кнопка
 * «открыть свою библиотеку») живут на реализации в `:common`.
 */
interface LibrarySourcesComponent {
    /** Возврат на главный экран. */
    fun onBack()
}
