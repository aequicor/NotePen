package ru.kyamshanov.notepen

/**
 * `true` на платформах, где блокировка ориентации экрана имеет смысл (Android —
 * экран физически поворачивается за датчиком, и его нужно уметь залочить). На
 * десктопе всегда `false`: ориентации окна нет, [ApplyScreenOrientation] там
 * no-op, поэтому переключатели ориентации не показываем.
 */
expect val SupportsScreenOrientation: Boolean
