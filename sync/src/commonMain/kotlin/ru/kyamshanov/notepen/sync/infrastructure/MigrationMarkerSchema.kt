package ru.kyamshanov.notepen.sync.infrastructure

import app.cash.sqldelight.db.SqlDriver

/**
 * Idempotently creates the `MigrationMarker` table on [driver].
 *
 * The table was introduced after the first releases to guard one-time data
 * migrations (see [PendingDeltaContentAddressedMigration]). Because `.sqm`
 * schema migrations are deliberately disabled in this project and the JVM
 * factory only runs `Schema.create()` on a fresh database, pre-existing
 * databases would otherwise lack the table. `CREATE TABLE IF NOT EXISTS` adds
 * it safely on every launch without a schema-version bump and is a no-op once
 * present.
 */
internal fun ensureMigrationMarkerTable(driver: SqlDriver) {
    driver.execute(
        identifier = null,
        sql =
            "CREATE TABLE IF NOT EXISTS MigrationMarker (" +
                "key TEXT NOT NULL PRIMARY KEY, " +
                "applied_at INTEGER NOT NULL)",
        parameters = 0,
    )
}
