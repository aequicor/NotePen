package ru.kyamshanov.notepen.reflow

import ru.kyamshanov.notepen.reflow.ui.ReflowLayoutCache

internal actual fun createReflowLayoutCache(): ReflowLayoutCache = FileSystemReflowLayoutCache()
