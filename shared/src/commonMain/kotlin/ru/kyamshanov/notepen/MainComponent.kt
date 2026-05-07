package ru.kyamshanov.notepen

/**
 * Контракт компонента главного экрана в Decompose-навигации.
 *
 * Объявляется в `:shared`, реализуется в `:common` через [MainScreenComponent].
 * Позволяет [RootComponent] ссылаться на компонент главного экрана без циклической
 * зависимости `:shared` → `:common`.
 */
interface MainComponent
