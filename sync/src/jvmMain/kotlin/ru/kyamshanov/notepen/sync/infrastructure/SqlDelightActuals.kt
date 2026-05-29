package ru.kyamshanov.notepen.sync.infrastructure

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import ru.kyamshanov.notepen.sync.db.NotePenSyncDatabase
import java.io.File

internal actual fun currentTimeMillis(): Long = System.currentTimeMillis()

/**
 * JVM entry point: opens (or creates) a SQLite database at [databasePath]
 * and returns a wired [NotePenSyncDatabase]. The schema is created on first
 * use; subsequent runs reuse the existing file.
 *
 * Call from `main.kt` once at startup; pass the result to
 * [SqlDelightPendingDeltaQueue].
 */
fun createSyncDatabaseJvm(databasePath: String): NotePenSyncDatabase {
    val file = File(databasePath)
    file.parentFile?.mkdirs()
    val freshInstall = !file.exists()
    val driver = JdbcSqliteDriver(url = "jdbc:sqlite:${file.absolutePath}")
    if (freshInstall) {
        NotePenSyncDatabase.Schema.create(driver)
    }
    // MigrationMarker landed after the first releases, but JVM only runs
    // Schema.create() on a fresh file (and `.sqm` schema migrations are
    // deliberately disabled — see CLAUDE.md). Create the table idempotently so
    // pre-existing databases gain it without a schema-version bump.
    ensureMigrationMarkerTable(driver)
    return NotePenSyncDatabase(driver)
}
