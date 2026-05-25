package ru.kyamshanov.notepen

import ru.kyamshanov.notepen.reflow.api.ReaderSettingsRepository

/** Creates the platform [ReaderSettingsRepository] backing the global reader settings store. */
expect fun createReaderSettingsRepository(): ReaderSettingsRepository
