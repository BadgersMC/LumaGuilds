package net.lumalyte.lg.infrastructure.persistence.storage

import co.aikar.idb.Database
import co.aikar.idb.DatabaseOptions
import java.io.File

/**
 * SQLite storage implementation used when virtual-thread-backed async work is enabled.
 *
 * Virtual threads improve caller scalability, but they do not change SQLite's
 * single-writer constraint. The shared SQLite pool therefore remains serialized.
 */
class VirtualThreadSQLiteStorage(dataFolder: File) : Storage<Database> {
    override val connection: Database
    override val dialect = SqlDialect.SQLITE

    init {
        val dbPath = "$dataFolder/lumaguilds.db"
        val options = DatabaseOptions.builder()
            .sqlite(dbPath)
            .build()

        connection = sqlitePool(options)
    }
}
