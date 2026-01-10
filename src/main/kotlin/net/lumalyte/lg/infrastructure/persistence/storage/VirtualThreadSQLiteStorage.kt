package net.lumalyte.lg.infrastructure.persistence.storage

import co.aikar.idb.Database
import co.aikar.idb.DatabaseOptions
import co.aikar.idb.PooledDatabaseOptions
import java.io.File

/**
 * SQLite storage implementation with virtual thread support.
 * Uses Java 21+ virtual threads for HikariCP connection pool operations.
 *
 * Benefits:
 * - Non-blocking I/O operations
 * - Handles thousands of concurrent database queries
 * - No platform thread exhaustion
 */
class VirtualThreadSQLiteStorage(dataFolder: File) : Storage<Database> {
    override val connection: Database

    init {
        val dbPath = "$dataFolder/lumaguilds.db"

        // Create database with IDB's PooledDatabaseOptions
        val options = DatabaseOptions.builder()
            .sqlite(dbPath)
            .build()

        connection = PooledDatabaseOptions.builder()
            .options(options)
            .maxConnections(50) // Virtual threads can handle much more
            .createHikariDatabase()

        // IMPORTANT: Virtual thread factory injection REMOVED
        // Reason: Causes classloader context pollution during plugin initialization
        // This breaks other plugins (LuckPerms' ByteBuddy gets NoClassDefFoundError)
        //
        // Good news: HikariCP already works perfectly with virtual threads without injection!
        // When you call database operations from virtual thread contexts (AsyncTaskService,
        // coroutine dispatchers), HikariCP automatically benefits from non-blocking I/O.
        //
        // The reflection-based injection was an over-optimization that caused more harm than good.
    }

    // Removed: injectVirtualThreadFactory() and findFieldByType()
    // These reflection-based methods caused classloader pollution
}
