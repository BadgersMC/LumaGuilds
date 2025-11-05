package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.GuildVaultRepository
import net.lumalyte.lg.infrastructure.persistence.storage.SQLiteStorage
import org.bukkit.inventory.ItemStack
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.sql.SQLException
import java.util.UUID
import java.util.Base64

/**
 * SQLite implementation of GuildVaultRepository.
 * Stores serialized ItemStacks in the guild_vault_items table.
 */
class GuildVaultRepositorySQLite(private val storage: SQLiteStorage) : GuildVaultRepository {

    init {
        createVaultItemsTable()
    }

    private fun createVaultItemsTable() {
        val sql = """
            CREATE TABLE IF NOT EXISTS guild_vault_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                guild_id TEXT NOT NULL,
                slot_index INTEGER NOT NULL,
                item_data TEXT NOT NULL,
                UNIQUE(guild_id, slot_index),
                FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
            );
        """.trimIndent()

        try {
            storage.connection.executeUpdate(sql)
            // Create index for faster lookups
            storage.connection.executeUpdate(
                "CREATE INDEX IF NOT EXISTS idx_vault_guild_id ON guild_vault_items(guild_id)"
            )
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to create guild_vault_items table", e)
        }
    }

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
        val sql = "SELECT slot_index, item_data FROM guild_vault_items WHERE guild_id = ?"
        val inventory = mutableMapOf<Int, ItemStack>()

        try {
            val results = storage.connection.getResults(sql, guildId.toString())
            for (row in results) {
                val slotIndex = row.getInt("slot_index")
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
            val deleteSql = "DELETE FROM guild_vault_items WHERE guild_id = ? AND slot_index = ?"
            return try {
                storage.connection.executeUpdate(deleteSql, guildId.toString(), slotIndex) >= 0
                true
            } catch (e: SQLException) {
                false
            }
        }

        val serializedItem = serializeItem(item) ?: return false

        val sql = """
            INSERT OR REPLACE INTO guild_vault_items (guild_id, slot_index, item_data)
            VALUES (?, ?, ?)
        """.trimIndent()

        return try {
            storage.connection.executeUpdate(sql, guildId.toString(), slotIndex, serializedItem) > 0
        } catch (e: SQLException) {
            false
        }
    }

    override fun getVaultItem(guildId: UUID, slotIndex: Int): ItemStack? {
        val sql = "SELECT item_data FROM guild_vault_items WHERE guild_id = ? AND slot_index = ?"

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
        val sql = "DELETE FROM guild_vault_items WHERE guild_id = ?"

        return try {
            storage.connection.executeUpdate(sql, guildId.toString())
            true
        } catch (e: SQLException) {
            false
        }
    }

    override fun getVaultItemCount(guildId: UUID): Int {
        val sql = "SELECT COUNT(*) as count FROM guild_vault_items WHERE guild_id = ?"

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

    /**
     * Serializes an ItemStack to a Base64-encoded string.
     */
    private fun serializeItem(item: ItemStack): String? {
        return try {
            val outputStream = ByteArrayOutputStream()
            val dataOutput = BukkitObjectOutputStream(outputStream)
            dataOutput.writeObject(item)
            dataOutput.close()
            Base64.getEncoder().encodeToString(outputStream.toByteArray())
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Deserializes a Base64-encoded string to an ItemStack.
     */
    private fun deserializeItem(data: String): ItemStack? {
        return try {
            val inputStream = ByteArrayInputStream(Base64.getDecoder().decode(data))
            val dataInput = BukkitObjectInputStream(inputStream)
            val item = dataInput.readObject() as ItemStack
            dataInput.close()
            item
        } catch (e: Exception) {
            null
        }
    }
}
