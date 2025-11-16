package net.lumalyte.lg.infrastructure.persistence.storage

import co.aikar.idb.Database
import co.aikar.idb.DatabaseOptions
import co.aikar.idb.PooledDatabaseOptions

/**
 * MariaDB/MySQL storage implementation with connection pooling.
 *
 * @param host Database host (e.g., "localhost")
 * @param port Database port (default: 3306)
 * @param database Database name
 * @param username Database username
 * @param password Database password
 * @param maxPoolSize Maximum connection pool size
 * @param minIdle Minimum idle connections
 * @param connectionTimeout Connection timeout in milliseconds
 * @param idleTimeout Idle timeout in milliseconds
 * @param maxLifetime Maximum connection lifetime in milliseconds
 */
class MariaDBStorage(
    host: String,
    port: Int = 3306,
    database: String,
    username: String,
    password: String,
    maxPoolSize: Int = 10,
    minIdle: Int = 2,
    connectionTimeout: Long = 30000,
    idleTimeout: Long = 600000,
    maxLifetime: Long = 1800000
) : Storage<Database> {
    override val connection: Database

    init {
        // Build DSN without jdbc: prefix (IDB adds it automatically)
        // Format for IDB: mariadb://host:port/database?params
        val dsn = buildString {
            append("mariadb://$host:$port/$database?")
            // Character encoding
            append("characterEncoding=utf8&")
            append("useUnicode=true")
        }

        // Use DatabaseOptions builder with custom DSN to bypass the mysql() method
        // which would create jdbc:mysql:// URLs incompatible with MariaDB driver
        val options = DatabaseOptions.builder()
            .driverClassName("org.mariadb.jdbc.Driver")
            .dataSourceClassName("org.mariadb.jdbc.MariaDbDataSource")
            .user(username)
            .pass(password)
            .dsn(dsn)  // IDB will prepend "jdbc:" automatically
            .useOptimizations(false) // Prevents MySQL-specific HikariCP properties
            .build()

        connection = PooledDatabaseOptions.builder()
            .options(options)
            .maxConnections(maxPoolSize)
            .createHikariDatabase()
    }
}
