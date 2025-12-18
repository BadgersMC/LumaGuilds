package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database

import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.GuildVaultRepository
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import org.bukkit.inventory.ItemStack
import java.sql.SQLException
import java.util.UUID
import java.util.Base64
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.ObjectInputStream
import java.util.zip.ZipException

/**
 * SQLite implementation of GuildVaultRepository.
 * Uses the new vault_slots and vault_gold tables created by migration version 12.
 * Provides crash-resistant storage with WAL mode enabled.
 */
class GuildVaultRepositorySQLite(private val storage: Storage<Database>) : GuildVaultRepository {
    private val logger = LoggerFactory.getLogger(GuildVaultRepositorySQLite::class.java)

    // Note: Tables are created by SQLiteMigrations.migrateToVersion12()
    // vault_slots, vault_gold, and vault_transaction_log tables are used

    override fun saveVaultInventory(guildId: UUID, items: Map<Int, ItemStack>): Boolean {
        // Use INSERT OR REPLACE for each slot - this is safe for concurrent access
        // and avoids the clear-then-insert race condition where one player's save
        // would delete another player's items
        for ((slot, item) in items) {
            if (!saveVaultItem(guildId, slot, item)) {
                return false
            }
        }

        return true
    }

    override fun getVaultInventory(guildId: UUID): Map<Int, ItemStack> {
        val sql = "SELECT slot, item_data FROM vault_slots WHERE guild_id = ?"
        val inventory = mutableMapOf<Int, ItemStack>()

        try {
            val results = storage.connection.getResults(sql, guildId.toString())
            for (row in results) {
                val slotIndex = row.getInt("slot")
                val itemData = row.getString("item_data")
                val item = deserializeItem(itemData)
                if (item != null) {
                    inventory[slotIndex] = item
                }
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to get vault inventory for guild $guildId", e)
        }

        return inventory
    }

    override fun saveVaultItem(guildId: UUID, slotIndex: Int, item: ItemStack?): Boolean {
        if (item == null) {
            // Remove item from slot
            val deleteSql = "DELETE FROM vault_slots WHERE guild_id = ? AND slot = ?"
            return try {
                storage.connection.executeUpdate(deleteSql, guildId.toString(), slotIndex) >= 0
                true
            } catch (e: SQLException) {
                false
            }
        }

        val serializedItem = serializeItem(item) ?: return false
        val currentTime = System.currentTimeMillis()

        val sql = """
            INSERT OR REPLACE INTO vault_slots (guild_id, slot, item_data, last_modified)
            VALUES (?, ?, ?, ?)
        """.trimIndent()

        return try {
            storage.connection.executeUpdate(sql, guildId.toString(), slotIndex, serializedItem, currentTime) > 0
        } catch (e: SQLException) {
            false
        }
    }

    override fun getVaultItem(guildId: UUID, slotIndex: Int): ItemStack? {
        val sql = "SELECT item_data FROM vault_slots WHERE guild_id = ? AND slot = ?"

        return try {
            val results = storage.connection.getResults(sql, guildId.toString(), slotIndex)
            if (results.isEmpty()) {
                null
            } else {
                val itemData = results[0].getString("item_data")
                deserializeItem(itemData)
            }
        } catch (e: SQLException) {
            null
        }
    }

    override fun clearVault(guildId: UUID): Boolean {
        return try {
            // Clear all vault slots
            storage.connection.executeUpdate(
                "DELETE FROM vault_slots WHERE guild_id = ?",
                guildId.toString()
            )

            // Clear gold balance
            storage.connection.executeUpdate(
                "DELETE FROM vault_gold WHERE guild_id = ?",
                guildId.toString()
            )

            true
        } catch (e: SQLException) {
            false
        }
    }

    override fun getVaultItemCount(guildId: UUID): Int {
        val sql = "SELECT COUNT(*) as count FROM vault_slots WHERE guild_id = ?"

        return try {
            val results = storage.connection.getResults(sql, guildId.toString())
            if (results.isEmpty()) 0 else results[0].getInt("count")
        } catch (e: SQLException) {
            0
        }
    }

    override fun hasVaultItems(guildId: UUID): Boolean {
        return getVaultItemCount(guildId) > 0
    }

    override fun getAllGuildIds(): List<UUID> {
        val sql = "SELECT DISTINCT guild_id FROM vault_slots UNION SELECT DISTINCT guild_id FROM vault_gold"

        return try {
            val results = storage.connection.getResults(sql)
            results.map { row -> UUID.fromString(row.getString("guild_id")) }
        } catch (e: SQLException) {
            emptyList()
        }
    }

    // ========== Gold Balance Management ==========

    override fun getGoldBalance(guildId: UUID): Long {
        val sql = "SELECT balance FROM vault_gold WHERE guild_id = ?"

        return try {
            val results = storage.connection.getResults(sql, guildId.toString())
            if (results.isEmpty()) {
                // Guild has no gold balance entry yet, return 0
                0L
            } else {
                // SQLite returns INTEGER as Int, not Long
                results[0].getInt("balance").toLong()
            }
        } catch (e: SQLException) {
            0L
        }
    }

    override fun setGoldBalance(guildId: UUID, balance: Long): Boolean {
        if (balance < 0) return false

        val currentTime = System.currentTimeMillis()
        val sql = """
            INSERT OR REPLACE INTO vault_gold (guild_id, balance, last_modified)
            VALUES (?, ?, ?)
        """.trimIndent()

        return try {
            storage.connection.executeUpdate(sql, guildId.toString(), balance, currentTime) > 0
        } catch (e: SQLException) {
            false
        }
    }

    override fun addGoldBalance(guildId: UUID, amount: Long): Long {
        if (amount < 0) return -1L

        val currentBalance = getGoldBalance(guildId)
        val newBalance = currentBalance + amount

        return if (setGoldBalance(guildId, newBalance)) {
            newBalance
        } else {
            -1L
        }
    }

    override fun subtractGoldBalance(guildId: UUID, amount: Long): Long {
        if (amount < 0) return -1L

        val currentBalance = getGoldBalance(guildId)
        if (currentBalance < amount) {
            // Insufficient balance
            return -1L
        }

        val newBalance = currentBalance - amount

        return if (setGoldBalance(guildId, newBalance)) {
            newBalance
        } else {
            -1L
        }
    }

    /**
     * Serializes an ItemStack to a Base64-encoded string using Paper's native NBT serialization.
     */
    private fun serializeItem(item: ItemStack): String? {
        return try {
            Base64.getEncoder().encodeToString(item.serializeAsBytes())
        } catch (e: Exception) {
            null
        }
    }

    private fun deserializeItem(data: String): ItemStack? {
        val bytes = try {
            Base64.getDecoder().decode(data)
        } catch (e: IllegalArgumentException) {
            logger.warn("Invalid Base64 data for item, skipping")
            return null
        }

        // Try Paper's native NBT format first (GZIP compressed)
        try {
            return ItemStack.deserializeBytes(bytes)
        } catch (e: RuntimeException) {
            if (e.cause is ZipException || e.message?.contains("GZIP") == true) {
                // Fall through to try legacy format
            } else {
                logger.warn("Failed to deserialize item with NBT format: ${e.message}")
                return null
            }
        }

        // Try legacy Java ObjectInputStream format
        try {
            val byteArrayInputStream = ByteArrayInputStream(bytes)
            val objectInputStream = ObjectInputStream(byteArrayInputStream)
            @Suppress("UNCHECKED_CAST")
            val serialized = objectInputStream.readObject() as Map<String, Any>
            objectInputStream.close()
            return ItemStack.deserialize(serialized)
        } catch (e: Exception) {
            // Legacy format failed - item is from an old/incompatible version or corrupted
            // This is expected when migrating from very old databases, safe to ignore
            logger.debug("Skipped item with unreadable legacy format (${e.javaClass.simpleName})")
            return null
        }
    }
}
