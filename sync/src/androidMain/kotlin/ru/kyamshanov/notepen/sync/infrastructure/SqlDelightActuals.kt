package ru.kyamshanov.notepen.sync.infrastructure

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import ru.kyamshanov.notepen.sync.db.NotePenSyncDatabase

internal actual fun currentTimeMillis(): Long = System.currentTimeMillis()

/**
 * Android entry point: opens (or creates) a SQLite database under the app's
 * private data directory and returns a wired [NotePenSyncDatabase].
 *
 * [AndroidSqliteDriver] handles schema creation and upgrades automatically.
 */
fun createSyncDatabaseAndroid(
    context: Context,
    databaseName: String = "notepen-sync.db",
): NotePenSyncDatabase {
    val driver =
        AndroidSqliteDriver(
            schema = NotePenSyncDatabase.Schema,
            context = context,
            name = databaseName,
        )
    // MigrationMarker landed after the first releases; `.sqm` schema migrations
    // are deliberately disabled (see CLAUDE.md), so create the table
    // idempotently for databases predating it. No-op once present.
    ensureMigrationMarkerTable(driver)
    return NotePenSyncDatabase(driver)
}
