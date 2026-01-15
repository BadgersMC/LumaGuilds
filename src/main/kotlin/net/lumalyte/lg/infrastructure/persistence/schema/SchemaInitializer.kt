package net.lumalyte.lg.infrastructure.persistence.schema

import co.aikar.idb.Database
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Initializes the database schema for LumaGuilds.
 *
 * This class handles:
 * - Initial schema creation from SQL file
 * - Schema version tracking
 * - Future migration support
 */
class SchemaInitializer(private val database: Database) {

    private val log = LoggerFactory.getLogger(SchemaInitializer::class.java)

    companion object {
        private const val CURRENT_SCHEMA_VERSION = 15
        private const val SCHEMA_FILE = "/schema-sqlite.sql"
    }

    /**
     * Initializes the database schema.
     * Creates all tables if they don't exist and sets up the schema version.
     *
     * @return true if initialization succeeded, false otherwise
     */
    fun initialize(): Boolean {
        return try {
            log.info("Initializing database schema...")

            // Check current schema version
            val currentVersion = getCurrentSchemaVersion()
            log.info("Current schema version: ${currentVersion ?: "none"}")

            if (currentVersion == null) {
                // Fresh database - create schema
                log.info("No existing schema found. Creating database schema from scratch...")
                createSchema()
                log.info("Database schema created successfully! Version: $CURRENT_SCHEMA_VERSION")
                true
            } else if (currentVersion < CURRENT_SCHEMA_VERSION) {
                // TODO: Implement migrations in the future
                log.warn("Schema version $currentVersion is outdated (current: $CURRENT_SCHEMA_VERSION)")
                log.warn("Schema migrations not yet implemented. Please manually update your database.")
                false
            } else {
                log.info("Database schema is up to date (version $currentVersion)")
                true
            }
        } catch (e: Exception) {
            log.error("Failed to initialize database schema!", e)
            false
        }
    }

    /**
     * Gets the current schema version from the database.
     *
     * @return The current schema version, or null if the table doesn't exist
     */
    private fun getCurrentSchemaVersion(): Int? {
        return try {
            val result = database.getFirstRow("SELECT version FROM schema_version LIMIT 1")
            result?.getInt("version")
        } catch (e: Exception) {
            // Table probably doesn't exist
            null
        }
    }

    /**
     * Creates the database schema from the SQL file.
     */
    private fun createSchema() {
        val schemaSQL = loadSchemaFile()

        // Split by semicolons to execute statements individually
        // SQLite doesn't support multiple statements in one execute
        val statements = schemaSQL
            .split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("--") }

        log.info("Executing ${statements.size} SQL statements...")

        var executedCount = 0
        for (statement in statements) {
            if (statement.isBlank()) continue

            try {
                database.executeUpdate(statement)
                executedCount++
            } catch (e: Exception) {
                // Log but continue - some statements might fail if tables already exist
                log.debug("Statement execution note: ${e.message}")
            }
        }

        log.info("Executed $executedCount SQL statements successfully")
    }

    /**
     * Loads the schema SQL file from resources.
     *
     * @return The schema SQL as a string
     */
    private fun loadSchemaFile(): String {
        val resource = javaClass.getResourceAsStream(SCHEMA_FILE)
            ?: throw IllegalStateException("Schema file not found: $SCHEMA_FILE")

        return BufferedReader(InputStreamReader(resource)).use { reader ->
            reader.readText()
        }
    }
}
