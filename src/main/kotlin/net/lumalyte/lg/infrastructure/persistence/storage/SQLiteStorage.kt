package net.lumalyte.lg.infrastructure.persistence.storage

import co.aikar.idb.Database
import co.aikar.idb.DatabaseOptions
import co.aikar.idb.PooledDatabaseOptions
import java.io.File

class SQLiteStorage(dataFolder: File): Storage<Database> {
    override val connection: Database
    override val dialect = SqlDialect.SQLITE

    init {
        val options = DatabaseOptions.builder().sqlite("$dataFolder/lumaguilds.db").build()
        connection = sqlitePool(options)
    }
}

internal fun sqlitePool(options: DatabaseOptions): Database =
    PooledDatabaseOptions.builder()
        .options(options)
        // WAL permits concurrent readers but SQLite still has a single writer.
        // Keep the pool deliberately small and make competing writers wait
        // instead of failing immediately with SQLITE_BUSY.
        .minIdleConnections(1)
        .maxConnections(4)
        .dataSourceProperties(mapOf("busyTimeout" to 15_000))
        .createHikariDatabase()
