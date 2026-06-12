package ru.kyamshanov.notepen.reflow.ui

/**
 * `true` на платформах, где блокировка ориентации экрана имеет смысл (Android —
 * экран физически поворачивается за датчиком). На десктопе всегда `false`:
 * ориентации окна нет, поэтому селектор ориентации в настройках ридера не
 * показываем. Платформенно (`expect/actual`), как [isReducedMotionEnabled].
 */
internal expect val SupportsReaderOrientation: Boolean
