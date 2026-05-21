package ru.kyamshanov.notepen

import ru.kyamshanov.notepen.annotation.domain.port.ToolPresetsRepository

/** Creates the platform [ToolPresetsRepository] backing the global tool presets store. */
expect fun createToolPresetsRepository(): ToolPresetsRepository
