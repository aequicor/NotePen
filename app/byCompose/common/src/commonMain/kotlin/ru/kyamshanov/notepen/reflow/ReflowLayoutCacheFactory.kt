package ru.kyamshanov.notepen.reflow

import ru.kyamshanov.notepen.reflow.ui.ReflowLayoutCache

/**
 * Создаёт платформенный дисковый кэш расчётной раскладки (`cr3cache`-стиль) —
 * `FileSystemReflowLayoutCache` на JVM/Android. Инжектится в `ReflowReader`
 * через `LocalReflowLayoutCache`.
 */
internal expect fun createReflowLayoutCache(): ReflowLayoutCache
