package ru.kyamshanov.notepen.annotation.domain.model

import kotlinx.serialization.Serializable

/**
 * A saved snapshot of [PenSettings] the user can re-apply with one tap.
 *
 * @property id Stable identifier. Built-in presets use an `"builtin:"`-prefixed
 *   key (see [BuiltinToolPresets]); user presets use a generated unique id.
 * @property settings Full pen configuration captured by this preset.
 */
@Serializable
data class PenPreset(val id: String, val settings: PenSettings)

/**
 * A saved snapshot of [MarkerSettings] the user can re-apply with one tap.
 *
 * @property id See [PenPreset.id].
 * @property settings Full marker configuration captured by this preset.
 */
@Serializable
data class MarkerPreset(val id: String, val settings: MarkerSettings)

/**
 * A saved snapshot of [EraserSettings] the user can re-apply with one tap.
 *
 * @property id See [PenPreset.id].
 * @property settings Full eraser configuration captured by this preset.
 */
@Serializable
data class EraserPreset(val id: String, val settings: EraserSettings)

/**
 * User-defined tool presets persisted globally (across all documents).
 *
 * Holds only custom presets — built-in presets live in code ([BuiltinToolPresets])
 * and are never serialised, so they can evolve between app versions.
 */
@Serializable
data class StoredToolPresets(
    val pen: List<PenPreset> = emptyList(),
    val marker: List<MarkerPreset> = emptyList(),
    val eraser: List<EraserPreset> = emptyList(),
)

/** `true` when [id] belongs to a built-in (non-deletable) preset. */
fun isBuiltinPresetId(id: String): Boolean = id.startsWith(BUILTIN_PRESET_PREFIX)

internal const val BUILTIN_PRESET_PREFIX = "builtin:"

/**
 * Built-in presets shipped with the app — sensible defaults for each tool so the
 * presets zone is useful before the user saves any of their own.
 *
 * Values are fractions of page width (see the companion-object docs on each
 * settings type); colours are packed ARGB.
 */
object BuiltinToolPresets {
    /** Default pen presets (max two): a blue and a red ballpoint. */
    val pen: List<PenPreset> =
        listOf(
            PenPreset("${BUILTIN_PRESET_PREFIX}pen:blue", PenSettings(colorArgb = 0xFF1E88E5L, strokeWidth = 0.0020f)),
            PenPreset("${BUILTIN_PRESET_PREFIX}pen:red", PenSettings(colorArgb = 0xFFE53935L, strokeWidth = 0.0020f)),
        )

    /** Default highlighter presets (max two) — translucent so text stays readable. */
    val marker: List<MarkerPreset> =
        listOf(
            MarkerPreset("${BUILTIN_PRESET_PREFIX}marker:yellow", MarkerSettings(colorArgb = 0x80FFEB3BL, strokeWidth = 0.025f)),
            MarkerPreset("${BUILTIN_PRESET_PREFIX}marker:green", MarkerSettings(colorArgb = 0x8076FF03L, strokeWidth = 0.025f)),
        )

    /** Default eraser presets (max two): a small point eraser and a whole-stroke eraser. */
    val eraser: List<EraserPreset> =
        listOf(
            EraserPreset(
                "${BUILTIN_PRESET_PREFIX}eraser:small",
                EraserSettings(shape = EraserShape.CIRCLE, sizeNormalized = 0.02f, mode = EraserMode.POINT),
            ),
            EraserPreset(
                "${BUILTIN_PRESET_PREFIX}eraser:object",
                EraserSettings(shape = EraserShape.CIRCLE, sizeNormalized = 0.04f, mode = EraserMode.OBJECT),
            ),
        )
}
