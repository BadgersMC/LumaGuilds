package net.lumalyte.lg.application.services

import co.aikar.idb.Database
import net.lumalyte.lg.application.persistence.GuildVaultRepository
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.util.io.BukkitObjectInputStream
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.sql.SQLException
import java.util.*

/**
 * Handles migration of vault data from the old system to the new system.
 * Detects old vault_contents table and migrates data to vault_slots and vault_gold.
 */
class VaultMigrationService(
    private val storage: Storage<Database>,
    private val vaultRepository: GuildVaultRepository
) {

    private val logger = LoggerFactory.getLogger(VaultMigrationService::class.java)

    /**
     * Checks if migration is needed by detecting the old vault_contents table.
     *
     * @return true if migration is required, false otherwise.
     */
    fun isMigrationNeeded(): Boolean {
        val sql = """
            SELECT name FROM sqlite_master
            WHERE type='table' AND name='vault_contents'
        """.trimIndent()

        return try {
            val results = storage.connection.getResults(sql)
            val hasOldTable = results.isNotEmpty()

            if (hasOldTable) {
                logger.info("Old vault_contents table detected - migration required")
            }

            hasOldTable
        } catch (e: SQLException) {
            logger.error("Failed to check for old vault table", e)
            false
        }
    }

    /**
     * Gets the database schema version from a metadata table.
     * Returns 0 if no version is set (pre-migration).
     *
     * @return The schema version number.
     */
    fun getSchemaVersion(): Int {
        // Check if schema_version table exists
        val checkTableSql = """
            SELECT name FROM sqlite_master
            WHERE type='table' AND name='vault_schema_version'
        """.trimIndent()

        try {
            val results = storage.connection.getResults(checkTableSql)
            if (results.isEmpty()) {
                // Table doesn't exist - create it
                createSchemaVersionTable()
                return 0
            }

            // Get version
            val getVersionSql = "SELECT version FROM vault_schema_version LIMIT 1"
            val versionResults = storage.connection.getResults(getVersionSql)

            return if (versionResults.isEmpty()) {
                0
            } else {
                versionResults[0].getInt("version")
            }
        } catch (e: SQLException) {
            logger.error("Failed to get schema version", e)
            return 0
        }
    }

    /**
     * Sets the database schema version.
     *
     * @param version The version number to set.
     */
    fun setSchemaVersion(version: Int) {
        val sql = """
            INSERT OR REPLACE INTO vault_schema_version (id, version, updated_at)
            VALUES (1, ?, ?)
        """.trimIndent()

        try {
            storage.connection.executeUpdate(sql, version, System.currentTimeMillis())
            logger.info("Updated vault schema version to $version")
        } catch (e: SQLException) {
            logger.error("Failed to set schema version", e)
        }
    }

    /**
     * Creates the schema_version table.
     */
    private fun createSchemaVersionTable() {
        val sql = """
            CREATE TABLE IF NOT EXISTS vault_schema_version (
                id INTEGER PRIMARY KEY,
                version INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
        """.trimIndent()

        try {
            storage.connection.executeUpdate(sql)
            logger.info("Created vault_schema_version table")
        } catch (e: SQLException) {
            logger.error("Failed to create schema version table", e)
        }
    }

    /**
     * Performs the migration from the old vault system to the new system.
     *
     * @return Migration result with success count and failure count.
     */
    fun performMigration(): MigrationResult {
        logger.info("╔════════════════════════════════════════════════════════════╗")
        logger.info("║ Starting Vault Migration                                  ║")
        logger.info("║ Old Format → New Format                                    ║")
        logger.info("╚════════════════════════════════════════════════════════════╝")

        val result = MigrationResult()

        try {
            // Get all old vault data
            val oldVaults = getOldVaultData()

            logger.info("Found ${oldVaults.size} vault(s) to migrate")

            oldVaults.forEach { (guildId, oldVaultData) ->
                try {
                    migrateVault(guildId, oldVaultData)
                    result.successCount++
                    logger.info("✓ Migrated vault for guild $guildId")
                } catch (e: Exception) {
                    result.failureCount++
                    result.failedGuilds.add(guildId)
                    logger.error("✗ Failed to migrate vault for guild $guildId", e)
                }
            }

            // Mark migration as complete
            setSchemaVersion(1)

            logger.info("╔════════════════════════════════════════════════════════════╗")
            logger.info("║ Migration Complete                                        ║")
            logger.info("║ Success: ${result.successCount.toString().padEnd(50)}║")
            logger.info("║ Failed: ${result.failureCount.toString().padEnd(51)}║")
            logger.info("╚════════════════════════════════════════════════════════════╝")

            if (result.failureCount == 0) {
                backupAndDropOldTables()
            } else {
                logger.warn("Migration had failures - old tables NOT dropped")
                logger.warn("Failed guilds: ${result.failedGuilds.joinToString(", ")}")
            }

        } catch (e: Exception) {
            logger.error("Critical error during migration", e)
            result.criticalError = e.message
        }

        return result
    }

    /**
     * Gets all vault data from the old vault_contents table.
     *
     * @return Map of guild ID to old vault data.
     */
    private fun getOldVaultData(): Map<UUID, OldVaultData> {
        val sql = "SELECT guild_id, inventory_blob FROM vault_contents"
        val vaults = mutableMapOf<UUID, OldVaultData>()

        try {
            val results = storage.connection.getResults(sql)
            for (row in results) {
                val guildId = UUID.fromString(row.getString("guild_id"))
                // Get as string (Base64 encoded) and decode to bytes
                val blobString = row.getString("inventory_blob")
                val blob = Base64.getDecoder().decode(blobString)

                vaults[guildId] = OldVaultData(blob)
            }
        } catch (e: SQLException) {
            logger.error("Failed to get old vault data", e)
        }

        return vaults
    }

    /**
     * Migrates a single vault from old format to new format.
     *
     * @param guildId The guild ID.
     * @param oldData The old vault data.
     */
    private fun migrateVault(guildId: UUID, oldData: OldVaultData) {
        // Deserialize old inventory blob
        val items = deserializeOldFormat(oldData.inventoryBlob)

        // Extract and remove gold items
        val goldBalance = extractAndRemoveGold(items)

        // Save items to new vault_slots table
        items.forEachIndexed { slot, item ->
            if (item != null && slot != 0) { // Skip slot 0 (reserved for gold button)
                vaultRepository.saveVaultItem(guildId, slot, item)
            }
        }

        // Save gold balance to vault_gold table
        if (goldBalance > 0) {
            vaultRepository.setGoldBalance(guildId, goldBalance)
        }
    }

    /**
     * Deserializes the old BLOB inventory format.
     * The old format stored the entire inventory as a Base64-encoded BLOB.
     *
     * @param blob The BLOB data.
     * @return Array of ItemStacks (may contain nulls for empty slots).
     */
    private fun deserializeOldFormat(blob: ByteArray): Array<ItemStack?> {
        return try {
            val inputStream = ByteArrayInputStream(blob)
            val dataInput = BukkitObjectInputStream(inputStream)
            val size = dataInput.readInt()
            val items = arrayOfNulls<ItemStack>(size)

            for (i in 0 until size) {
                val hasItem = dataInput.readBoolean()
                if (hasItem) {
                    items[i] = dataInput.readObject() as ItemStack
                }
            }

            dataInput.close()
            items
        } catch (e: Exception) {
            logger.error("Failed to deserialize old vault format", e)
            emptyArray()
        }
    }

    /**
     * Extracts all gold items from the inventory and converts them to nuggets.
     * Removes the gold items from the array.
     *
     * @param items Array of ItemStacks.
     * @return Total gold value in nuggets.
     */
    private fun extractAndRemoveGold(items: Array<ItemStack?>): Long {
        var totalNuggets = 0L

        items.forEachIndexed { index, item ->
            if (item != null) {
                when (item.type) {
                    Material.GOLD_BLOCK -> {
                        totalNuggets += item.amount * 81L
                        items[index] = null
                    }
                    Material.GOLD_INGOT -> {
                        totalNuggets += item.amount * 9L
                        items[index] = null
                    }
                    Material.GOLD_NUGGET -> {
                        totalNuggets += item.amount.toLong()
                        items[index] = null
                    }
                    else -> {
                        // Keep item as-is
                    }
                }
            }
        }

        return totalNuggets
    }

    /**
     * Backs up old tables and drops them after successful migration.
     */
    private fun backupAndDropOldTables() {
        try {
            // Create backup table
            val backupSql = """
                CREATE TABLE IF NOT EXISTS vault_contents_backup AS
                SELECT * FROM vault_contents
            """.trimIndent()

            storage.connection.executeUpdate(backupSql)
            logger.info("Created backup table: vault_contents_backup")

            // Drop old table
            val dropSql = "DROP TABLE IF EXISTS vault_contents"
            storage.connection.executeUpdate(dropSql)
            logger.info("Dropped old table: vault_contents")

        } catch (e: SQLException) {
            logger.error("Failed to backup/drop old tables", e)
        }
    }

    /**
     * Represents old vault data from the vault_contents table.
     */
    data class OldVaultData(
        val inventoryBlob: ByteArray
    )

    /**
     * Result of a migration operation.
     */
    data class MigrationResult(
        var successCount: Int = 0,
        var failureCount: Int = 0,
        val failedGuilds: MutableList<UUID> = mutableListOf(),
        var criticalError: String? = null
    ) {
        fun wasSuccessful(): Boolean = failureCount == 0 && criticalError == null
    }
}
