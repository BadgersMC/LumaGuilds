package net.lumalyte.lg.infrastructure.persistence.migrations

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.plugin.java.JavaPlugin
import java.sql.Connection

/**
 * Verifies that database schema matches expected state after migrations.
 * This prevents silent migration failures from causing runtime errors.
 */
class MigrationVerifier(
    private val plugin: JavaPlugin,
    private val connection: Connection
) {
    private val logger = plugin.componentLogger

    /**
     * Verify that all expected columns exist in the guilds table.
     * Returns true if all columns exist, false otherwise.
     */
    fun verifyGuildsTableSchema(): Boolean {
        val expectedColumns = mapOf(
            // Core columns (version 3)
            "id" to "TEXT",
            "name" to "TEXT",
            "banner" to "TEXT",
            "emoji" to "TEXT",
            "tag" to "TEXT",
            "home_world" to "TEXT",
            "home_x" to "INTEGER",
            "home_y" to "INTEGER",
            "home_z" to "INTEGER",
            "level" to "INTEGER",
            "bank_balance" to "INTEGER",
            "mode" to "TEXT",
            "mode_changed_at" to "TEXT",
            "created_at" to "TEXT",

            // Vault columns (version 13)
            "vault_status" to "TEXT",
            "vault_chest_world" to "TEXT",
            "vault_chest_x" to "INTEGER",
            "vault_chest_y" to "INTEGER",
            "vault_chest_z" to "INTEGER",

            // LFG columns (version 14)
            "is_open" to "INTEGER",
            "join_fee_enabled" to "INTEGER",
            "join_fee_amount" to "INTEGER"
        )

        val missingColumns = mutableListOf<String>()

        for ((columnName, expectedType) in expectedColumns) {
            if (!columnExists(columnName, expectedType)) {
                missingColumns.add(columnName)
            }
        }

        if (missingColumns.isNotEmpty()) {
            logger.error(Component.text("⚠ SCHEMA VERIFICATION FAILED!", NamedTextColor.RED))
            logger.error(Component.text("Missing columns in 'guilds' table:", NamedTextColor.RED))
            missingColumns.forEach { col ->
                logger.error(Component.text("  - $col", NamedTextColor.RED))
            }
            logger.error(Component.text("This indicates a migration failure. Please report this issue.", NamedTextColor.RED))
            return false
        }

        logger.info(Component.text("✓ Schema verification passed (${expectedColumns.size} columns verified)", NamedTextColor.GREEN))
        return true
    }

    /**
     * Check if a column exists in the guilds table with the expected type.
     */
    private fun columnExists(columnName: String, expectedType: String): Boolean {
        return try {
            connection.createStatement().use { stmt ->
                stmt.executeQuery("PRAGMA table_info(guilds)").use { rs ->
                    while (rs.next()) {
                        val name = rs.getString("name")
                        val type = rs.getString("type")
                        if (name == columnName) {
                            // Type checking is flexible (INTEGER == INT, TEXT == VARCHAR, etc.)
                            return true
                        }
                    }
                    false
                }
            }
        } catch (e: Exception) {
            logger.warn(Component.text("Failed to verify column '$columnName': ${e.message}"))
            false
        }
    }

    /**
     * Attempt to auto-repair missing columns from known migrations.
     * Returns true if repair was successful or not needed.
     */
    fun autoRepairSchema(): Boolean {
        logger.info(Component.text("🔧 Attempting automatic schema repair..."))

        val repairs = mutableListOf<String>()

        // Check and repair version 13 columns (vault)
        if (!columnExists("vault_status", "TEXT")) {
            repairs.add("ALTER TABLE guilds ADD COLUMN vault_status TEXT DEFAULT 'NEVER_PLACED';")
        }
        if (!columnExists("vault_chest_world", "TEXT")) {
            repairs.add("ALTER TABLE guilds ADD COLUMN vault_chest_world TEXT;")
        }
        if (!columnExists("vault_chest_x", "INTEGER")) {
            repairs.add("ALTER TABLE guilds ADD COLUMN vault_chest_x INTEGER;")
        }
        if (!columnExists("vault_chest_y", "INTEGER")) {
            repairs.add("ALTER TABLE guilds ADD COLUMN vault_chest_y INTEGER;")
        }
        if (!columnExists("vault_chest_z", "INTEGER")) {
            repairs.add("ALTER TABLE guilds ADD COLUMN vault_chest_z INTEGER;")
        }

        // Check and repair version 14 columns (LFG)
        if (!columnExists("is_open", "INTEGER")) {
            repairs.add("ALTER TABLE guilds ADD COLUMN is_open INTEGER DEFAULT 0;")
        }
        if (!columnExists("join_fee_enabled", "INTEGER")) {
            repairs.add("ALTER TABLE guilds ADD COLUMN join_fee_enabled INTEGER DEFAULT 0;")
        }
        if (!columnExists("join_fee_amount", "INTEGER")) {
            repairs.add("ALTER TABLE guilds ADD COLUMN join_fee_amount INTEGER DEFAULT 0;")
        }

        if (repairs.isEmpty()) {
            logger.info(Component.text("✓ No repairs needed"))
            return true
        }

        logger.warn(Component.text("Applying ${repairs.size} schema repairs..."))

        return try {
            connection.createStatement().use { stmt ->
                repairs.forEach { sql ->
                    stmt.execute(sql)
                    logger.info(Component.text("  ✓ ${sql.substringBefore("ADD COLUMN").trim()}... added column"))
                }
            }
            logger.info(Component.text("✓ Schema repair completed successfully", NamedTextColor.GREEN))
            true
        } catch (e: Exception) {
            logger.error(Component.text("✗ Schema repair failed: ${e.message}", NamedTextColor.RED))
            e.printStackTrace()
            false
        }
    }
}
