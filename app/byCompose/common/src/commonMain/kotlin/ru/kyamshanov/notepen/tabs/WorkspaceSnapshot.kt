package ru.kyamshanov.notepen.tabs

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Serializable, Compose-free snapshot of the whole workspace: every panel,
 * its tabs (file path + label + active one), the [LayoutTemplate] and the
 * draggable [ratios]. Persisted via `rememberSaveable` so the full split
 * survives the editor being torn down and recreated when the user opens the
 * library to add a tab.
 *
 * Identifiers ([PanelId] / [DocumentId]) are intentionally **not** stored —
 * they are session-scoped and regenerated on restore.
 *
 * @property template name of the [LayoutTemplate] in effect.
 * @property focusedPanelIndex index into [panels] of the focused panel.
 * @property ratios divider positions (see [WorkspaceLayout.ratios]).
 * @property panels the panels, in slot order.
 */
@kotlinx.serialization.Serializable
data class WorkspaceSnapshot(
    val template: String,
    val focusedPanelIndex: Int,
    val ratios: List<Float>,
    val panels: List<PanelSnapshot>,
) {
    /** One panel's tabs plus which one is active. */
    @kotlinx.serialization.Serializable
    data class PanelSnapshot(
        val activeTabIndex: Int,
        val tabs: List<TabSnapshot>,
    )

    /** One tab: the file to reopen and its label. */
    @kotlinx.serialization.Serializable
    data class TabSnapshot(
        val filePath: String,
        val displayName: String,
    )

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Encodes [snapshot] to a compact JSON string for `rememberSaveable`. */
        fun encode(snapshot: WorkspaceSnapshot): String = json.encodeToString(snapshot)

        /** Decodes a snapshot from [encoded], or `null` when blank / malformed. */
        fun decode(encoded: String): WorkspaceSnapshot? {
            if (encoded.isBlank()) return null
            return try {
                json.decodeFromString<WorkspaceSnapshot>(encoded)
            } catch (_: SerializationException) {
                null
            }
        }
    }
}

/** Captures [this] layout as a [WorkspaceSnapshot] (drops session-scoped ids). */
fun WorkspaceLayout.toSnapshot(): WorkspaceSnapshot =
    WorkspaceSnapshot(
        template = template.name,
        focusedPanelIndex = panels.indexOfFirst { it.id == focusedPanelId }.coerceAtLeast(0),
        ratios = ratios,
        panels = panels.map { panel ->
            WorkspaceSnapshot.PanelSnapshot(
                activeTabIndex = panel.tabs.tabs
                    .indexOfFirst { it.id == panel.tabs.activeId }
                    .coerceAtLeast(0),
                tabs = panel.tabs.tabs.map { tab ->
                    WorkspaceSnapshot.TabSnapshot(filePath = tab.filePath, displayName = tab.displayName)
                },
            )
        },
    )
