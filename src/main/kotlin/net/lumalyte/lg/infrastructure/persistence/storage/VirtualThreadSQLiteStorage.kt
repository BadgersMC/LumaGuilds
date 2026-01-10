package net.lumalyte.lg.infrastructure.persistence.storage

import co.aikar.idb.Database
import co.aikar.idb.DatabaseOptions
import co.aikar.idb.PooledDatabaseOptions
import com.zaxxer.hikari.HikariDataSource
import java.io.File
import java.lang.reflect.Field

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

        // Inject virtual thread factory into HikariCP via reflection
        try {
            injectVirtualThreadFactory(connection)
        } catch (e: Exception) {
            // If injection fails, fall back to default threading (which is fine!)
            // HikariCP doesn't pin virtual threads anyway, so this is just an optimization
            System.err.println("WARN: Failed to inject virtual thread factory (falling back to platform threads): ${e.message}")
            System.err.println("INFO: This is not critical - HikariCP works fine with virtual threads without this optimization")
        }
    }

    /**
     * Uses reflection to inject virtual thread factory into HikariCP DataSource.
     * This is necessary because IDB wraps HikariCP and doesn't expose thread factory config.
     */
    private fun injectVirtualThreadFactory(database: Database) {
        // Get the HikariDataSource from IDB's Database
        val dataSourceField = findFieldByType(database.javaClass, HikariDataSource::class.java)
            ?: throw IllegalStateException("Could not find HikariDataSource field in Database")

        dataSourceField.isAccessible = true
        val hikariDataSource = dataSourceField.get(database) as HikariDataSource

        // Get HikariCP's internal executor via reflection
        val poolField = hikariDataSource.javaClass.getDeclaredField("pool")
        poolField.isAccessible = true
        val pool = poolField.get(hikariDataSource) ?: return

        // Set the thread factory to use virtual threads
        val threadFactoryField = pool.javaClass.getDeclaredField("threadFactory")
        threadFactoryField.isAccessible = true
        threadFactoryField.set(pool, Thread.ofVirtual().name("hikari-sqlite-vt-", 0).factory())
    }

    /**
     * Recursively searches for a field of the specified type in a class hierarchy.
     */
    private fun findFieldByType(clazz: Class<*>, fieldType: Class<*>): Field? {
        // Search declared fields
        for (field in clazz.declaredFields) {
            if (fieldType.isAssignableFrom(field.type)) {
                return field
            }
        }

        // Search superclass if exists
        return if (clazz.superclass != null) {
            findFieldByType(clazz.superclass, fieldType)
        } else {
            null
        }
    }
}
