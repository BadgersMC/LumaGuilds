package net.lumalyte.lg.infrastructure.persistence.migrations

import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

/**
 * Utility for migrating data from SQLite to MariaDB.
 * Useful for transitioning from beta (SQLite) to production (MariaDB).
 */
class DatabaseMigrationUtility(
    private val plugin: JavaPlugin,
    private val sqliteFile: File,
    private val mariadbHost: String,
    private val mariadbPort: Int,
    private val mariadbDatabase: String,
    private val mariadbUsername: String,
    private val mariadbPassword: String
) {
    private val logger = plugin.logger
    private var sqliteConnection: Connection? = null
    private var mariadbConnection: Connection? = null

    /**
     * Migrates all data from SQLite to MariaDB.
     * Returns a migration report with statistics.
     */
    fun migrate(): MigrationReport {
        val report = MigrationReport()

        try {
            logger.info("=== Starting Database Migration: SQLite → MariaDB ===")

            // Connect to both databases
            logger.info("Connecting to SQLite database...")
            sqliteConnection = DriverManager.getConnection("jdbc:sqlite:${sqliteFile.absolutePath}")
            report.addStep("SQLite connection established")

            logger.info("Connecting to MariaDB database...")
            val mariadbUrl = "jdbc:mariadb://$mariadbHost:$mariadbPort/$mariadbDatabase?useSSL=false&allowPublicKeyRetrieval=true"
            mariadbConnection = DriverManager.getConnection(mariadbUrl, mariadbUsername, mariadbPassword)
            mariadbConnection?.autoCommit = false
            report.addStep("MariaDB connection established")

            // Verify MariaDB schema exists
            logger.info("Verifying MariaDB schema...")
            if (!verifyMariaDBSchema()) {
                report.addError("MariaDB schema not initialized. Please start the server with MariaDB configured first.")
                return report
            }
            report.addStep("MariaDB schema verified")

            // Disable foreign key checks during migration
            mariadbConnection?.createStatement()?.execute("SET FOREIGN_KEY_CHECKS=0")

            // Migrate tables in order (respecting foreign key dependencies)
            val tables = listOf(
                "guilds",
                "ranks",
                "members",
                "guild_invitations",
                "relations",
                "bank_tx",
                "kills",
                "wars",
                "leaderboards",
                "parties",
                "player_party_preferences",
                "guild_progression",
                "experience_transactions",
                "guild_activity_metrics",
                "guild_vault_items",
                "audits",
                "claims",
                "claim_partitions",
                "claim_default_permissions",
                "claim_flags",
                "claim_player_permissions"
            )

            for (table in tables) {
                if (tableExistsInSQLite(table)) {
                    logger.info("Migrating table: $table")
                    val count = migrateTable(table)
                    report.addMigratedTable(table, count)
                    logger.info("Migrated $count rows from $table")
                } else {
                    logger.info("Skipping $table (not found in SQLite)")
                }
            }

            // Re-enable foreign key checks
            mariadbConnection?.createStatement()?.execute("SET FOREIGN_KEY_CHECKS=1")

            // Commit transaction
            mariadbConnection?.commit()
            report.addStep("Transaction committed successfully")

            logger.info("=== Migration Completed Successfully ===")
            logger.info("Total tables migrated: ${report.migratedTables.size}")
            logger.info("Total rows migrated: ${report.totalRows}")

            report.success = true

        } catch (e: Exception) {
            logger.severe("Migration failed: ${e.message}")
            e.printStackTrace()
            report.addError("Migration failed: ${e.message}")

            try {
                mariadbConnection?.rollback()
                report.addStep("Transaction rolled back")
            } catch (rb: SQLException) {
                logger.severe("Failed to rollback: ${rb.message}")
            }
        } finally {
            // Close connections
            try {
                sqliteConnection?.close()
                mariadbConnection?.close()
            } catch (e: SQLException) {
                logger.severe("Failed to close connections: ${e.message}")
            }
        }

        return report
    }

    private fun verifyMariaDBSchema(): Boolean {
        val sql = """
            SELECT COUNT(*) FROM information_schema.tables
            WHERE table_schema = DATABASE()
            AND table_name = 'guilds'
        """.trimIndent()

        mariadbConnection?.createStatement()?.use { stmt ->
            stmt.executeQuery(sql).use { rs ->
                return rs.next() && rs.getInt(1) > 0
            }
        }
        return false
    }

    private fun tableExistsInSQLite(tableName: String): Boolean {
        val sql = "SELECT name FROM sqlite_master WHERE type='table' AND name=?"
        sqliteConnection?.prepareStatement(sql)?.use { stmt ->
            stmt.setString(1, tableName)
            stmt.executeQuery().use { rs ->
                return rs.next()
            }
        }
        return false
    }

    private fun migrateTable(tableName: String): Int {
        var rowCount = 0

        // Clear existing data in MariaDB table
        mariadbConnection?.createStatement()?.use { stmt ->
            stmt.execute("DELETE FROM $tableName")
        }

        // Get column names from both databases and find common columns
        val sqliteColumns = getSQLiteTableColumns(tableName)
        val mariadbColumns = getMariaDBTableColumns(tableName)

        // Only migrate columns that exist in BOTH databases
        val commonColumns = sqliteColumns.intersect(mariadbColumns.toSet()).toList()

        if (commonColumns.isEmpty()) {
            logger.warning("No common columns found for table $tableName")
            return 0
        }

        // Log if there are columns that won't be migrated
        val onlyInSQLite = sqliteColumns.subtract(mariadbColumns.toSet())
        if (onlyInSQLite.isNotEmpty()) {
            logger.info("Skipping columns in SQLite not present in MariaDB for $tableName: ${onlyInSQLite.joinToString(", ")}")
        }

        // Get datetime columns in MariaDB
        val datetimeColumns = getMariaDBDateTimeColumns(tableName)

        val columnList = commonColumns.joinToString(", ")
        val placeholders = commonColumns.joinToString(", ") { "?" }

        // Read from SQLite
        val selectSql = "SELECT $columnList FROM $tableName"
        sqliteConnection?.createStatement()?.use { stmt ->
            stmt.executeQuery(selectSql).use { rs ->
                // Prepare insert statement for MariaDB
                val insertSql = "INSERT INTO $tableName ($columnList) VALUES ($placeholders)"
                mariadbConnection?.prepareStatement(insertSql)?.use { insertStmt ->

                    while (rs.next()) {
                        // Copy all common columns
                        for (i in 1..commonColumns.size) {
                            val value = rs.getObject(i)
                            val columnName = commonColumns[i - 1]

                            // Only convert datetime for columns that are actually DATETIME in MariaDB
                            val convertedValue = if (datetimeColumns.contains(columnName)) {
                                when {
                                    value is String && isISO8601DateTime(value) -> convertISO8601ToDateTime(value)
                                    value is String && isUnixTimestamp(value) -> convertUnixTimestampToDateTime(value)
                                    value is Long -> convertUnixTimestampToDateTime(value.toString())
                                    value is Int -> convertUnixTimestampToDateTime(value.toString())
                                    else -> value
                                }
                            } else {
                                value
                            }

                            insertStmt.setObject(i, convertedValue)
                        }

                        insertStmt.executeUpdate()
                        rowCount++
                    }
                }
            }
        }

        return rowCount
    }

    private fun getSQLiteTableColumns(tableName: String): List<String> {
        val columns = mutableListOf<String>()

        // Get columns from SQLite
        val sql = "PRAGMA table_info($tableName)"
        sqliteConnection?.createStatement()?.use { stmt ->
            stmt.executeQuery(sql).use { rs ->
                while (rs.next()) {
                    columns.add(rs.getString("name"))
                }
            }
        }

        return columns
    }

    private fun getMariaDBTableColumns(tableName: String): List<String> {
        val columns = mutableListOf<String>()

        // Get columns from MariaDB
        val sql = """
            SELECT COLUMN_NAME
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME = ?
            ORDER BY ORDINAL_POSITION
        """.trimIndent()

        mariadbConnection?.prepareStatement(sql)?.use { stmt ->
            stmt.setString(1, tableName)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    columns.add(rs.getString("COLUMN_NAME"))
                }
            }
        }

        return columns
    }

    private fun getMariaDBDateTimeColumns(tableName: String): Set<String> {
        val datetimeColumns = mutableSetOf<String>()

        // Get datetime columns from MariaDB
        val sql = """
            SELECT COLUMN_NAME
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME = ?
            AND DATA_TYPE IN ('datetime', 'timestamp')
        """.trimIndent()

        mariadbConnection?.prepareStatement(sql)?.use { stmt ->
            stmt.setString(1, tableName)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    datetimeColumns.add(rs.getString("COLUMN_NAME"))
                }
            }
        }

        return datetimeColumns
    }

    /**
     * Checks if a string is in ISO-8601 datetime format
     */
    private fun isISO8601DateTime(value: String): Boolean {
        return value.matches(Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z?"))
    }

    /**
     * Checks if a string is a Unix timestamp (milliseconds)
     */
    private fun isUnixTimestamp(value: String): Boolean {
        return value.matches(Regex("\\d{10,13}"))
    }

    /**
     * Converts ISO-8601 datetime string to MariaDB DATETIME format
     * Example: 2025-10-31T21:03:40.641878474Z -> 2025-10-31 21:03:40
     */
    private fun convertISO8601ToDateTime(iso8601: String): String {
        return try {
            val instant = java.time.Instant.parse(iso8601)
            val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(java.time.ZoneId.of("UTC"))
            formatter.format(instant)
        } catch (e: Exception) {
            // If parsing fails, try to extract the date and time manually
            iso8601.replace("T", " ").replace("Z", "").substringBefore(".")
        }
    }

    /**
     * Converts Unix timestamp (milliseconds) to MariaDB DATETIME format
     * Example: 1761945065218 -> 2025-10-31 21:03:40
     */
    private fun convertUnixTimestampToDateTime(timestamp: String): String {
        return try {
            val millis = timestamp.toLong()
            val instant = java.time.Instant.ofEpochMilli(millis)
            val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(java.time.ZoneId.of("UTC"))
            formatter.format(instant)
        } catch (e: Exception) {
            // Return a default value if conversion fails
            "1970-01-01 00:00:00"
        }
    }
}

/**
 * Report of migration progress and results.
 */
data class MigrationReport(
    var success: Boolean = false,
    val steps: MutableList<String> = mutableListOf(),
    val errors: MutableList<String> = mutableListOf(),
    val migratedTables: MutableMap<String, Int> = mutableMapOf()
) {
    val totalRows: Int
        get() = migratedTables.values.sum()

    fun addStep(step: String) {
        steps.add(step)
    }

    fun addError(error: String) {
        errors.add(error)
    }

    fun addMigratedTable(table: String, rowCount: Int) {
        migratedTables[table] = rowCount
    }

    fun printReport(logger: java.util.logging.Logger) {
        logger.info("=== Migration Report ===")
        logger.info("Success: $success")
        logger.info("")

        if (steps.isNotEmpty()) {
            logger.info("Steps:")
            steps.forEach { logger.info("  - $it") }
            logger.info("")
        }

        if (migratedTables.isNotEmpty()) {
            logger.info("Migrated Tables:")
            migratedTables.forEach { (table, count) ->
                logger.info("  - $table: $count rows")
            }
            logger.info("Total rows: $totalRows")
            logger.info("")
        }

        if (errors.isNotEmpty()) {
            logger.info("Errors:")
            errors.forEach { logger.severe("  - $it") }
        }
    }
}
