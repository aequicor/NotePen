package ru.kyamshanov.notepen.session

import kotlinx.serialization.Serializable
import ru.kyamshanov.notepen.tabs.TabViewState
import ru.kyamshanov.notepen.tabs.WorkspaceSnapshot

/**
 * One restorable workspace: the panel/tab [layout] plus each open tab's view
 * position. The view states are stored parallel to the layout so that a restore
 * can pair every tab with its saved scroll/zoom.
 *
 * @property layout the panel split and the tabs open in each panel; reuses the
 *   existing [WorkspaceSnapshot] (session-scoped ids are regenerated on restore).
 * @property tabViewStates per-tab view positions, indexed
 *   `tabViewStates[panelIndex][tabIndex]` parallel to
 *   `layout.panels[panelIndex].tabs[tabIndex]`.
 * @property schemaVersion on-disk schema version, bumped on incompatible changes.
 */
@Serializable
data class SessionData(
    val layout: WorkspaceSnapshot,
    val tabViewStates: List<List<TabViewState>>,
    val schemaVersion: Int = 1,
)

/**
 * A user-named, persisted session: a [SessionData] tagged with a stable id, a
 * display [name] and the wall-clock time it was saved.
 *
 * The save time is caller-supplied (see the project's multiplatform epoch-millis
 * source) — the repository never generates time itself.
 *
 * @property id stable identifier; upserts in the repository are keyed by it.
 * @property name user-facing session name.
 * @property savedAtEpochMs wall-clock save time in milliseconds since the Unix epoch.
 * @property data the workspace payload to restore.
 */
@Serializable
data class NamedSession(
    val id: String,
    val name: String,
    val savedAtEpochMs: Long,
    val data: SessionData,
)
