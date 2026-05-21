package ru.kyamshanov.notepen.annotation.domain.port

import ru.kyamshanov.notepen.annotation.domain.model.StoredToolPresets

/**
 * Persistence port for the user's global tool presets (shared across documents).
 *
 * Only user-defined presets are stored — built-in presets live in code.
 * Implementations are platform adapters in the infrastructure layer.
 */
interface ToolPresetsRepository {

    /**
     * Reads the stored user presets.
     * Never throws — on any I/O or parse error it returns an empty [StoredToolPresets].
     */
    suspend fun load(): StoredToolPresets

    /**
     * Persists [presets] atomically, replacing the previous content.
     *
     * @throws Exception if the underlying write fails.
     */
    suspend fun save(presets: StoredToolPresets)
}
