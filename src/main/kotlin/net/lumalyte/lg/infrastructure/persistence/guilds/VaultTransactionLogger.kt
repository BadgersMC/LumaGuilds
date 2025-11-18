package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import org.bukkit.inventory.ItemStack
import org.bukkit.util.io.BukkitObjectOutputStream
import java.io.ByteArrayOutputStream
import java.sql.SQLException
import java.util.Base64
import java.util.UUID

/**
 * Transaction types for vault operations.
 */
enum class VaultTransactionType {
    GOLD_DEPOSIT,
    GOLD_WITHDRAW,
    ITEM_ADD,
    ITEM_REMOVE
}

/**
 * Data class representing a vault transaction.
 */
data class VaultTransaction(
    val id: String,
    val guildId: UUID,
    val playerId: UUID,
    val transactionType: VaultTransactionType,
    val amount: Long?,
    val itemData: String?,
    val slot: Int?,
    val timestamp: Long
)

/**
 * Logs all vault transactions to the vault_transaction_log table.
 * Provides append-only audit trail for gold deposits/withdrawals and item adds/removes.
 */
class VaultTransactionLogger(private val storage: Storage<Database>) {

    /**
     * Logs a gold transaction (deposit or withdrawal).
     *
     * @param guildId The ID of the guild.
     * @param playerId The ID of the player who performed the transaction.
     * @param transactionType The type of transaction (GOLD_DEPOSIT or GOLD_WITHDRAW).
     * @param amount The amount of gold in nuggets.
     * @return true if logged successfully, false otherwise.
     */
    fun logGoldTransaction(
        guildId: UUID,
        playerId: UUID,
        transactionType: VaultTransactionType,
        amount: Long
    ): Boolean {
        require(transactionType == VaultTransactionType.GOLD_DEPOSIT || transactionType == VaultTransactionType.GOLD_WITHDRAW) {
            "Transaction type must be GOLD_DEPOSIT or GOLD_WITHDRAW"
        }

        val transactionId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        val sql = """
            INSERT INTO vault_transaction_log (id, guild_id, player_id, transaction_type, amount, item_data, slot, timestamp)
            VALUES (?, ?, ?, ?, ?, NULL, NULL, ?)
        """.trimIndent()

        return try {
            storage.connection.executeUpdate(
                sql,
                transactionId,
                guildId.toString(),
                playerId.toString(),
                transactionType.name,
                amount,
                timestamp
            ) > 0
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to log gold transaction for guild $guildId", e)
        }
    }

    /**
     * Logs an item transaction (add or remove).
     *
     * @param guildId The ID of the guild.
     * @param playerId The ID of the player who performed the transaction.
     * @param transactionType The type of transaction (ITEM_ADD or ITEM_REMOVE).
     * @param item The ItemStack that was added or removed.
     * @param slot The slot index where the item was added/removed.
     * @return true if logged successfully, false otherwise.
     */
    fun logItemTransaction(
        guildId: UUID,
        playerId: UUID,
        transactionType: VaultTransactionType,
        item: ItemStack,
        slot: Int
    ): Boolean {
        require(transactionType == VaultTransactionType.ITEM_ADD || transactionType == VaultTransactionType.ITEM_REMOVE) {
            "Transaction type must be ITEM_ADD or ITEM_REMOVE"
        }

        val transactionId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val serializedItem = serializeItem(item) ?: return false

        val sql = """
            INSERT INTO vault_transaction_log (id, guild_id, player_id, transaction_type, amount, item_data, slot, timestamp)
            VALUES (?, ?, ?, ?, NULL, ?, ?, ?)
        """.trimIndent()

        return try {
            storage.connection.executeUpdate(
                sql,
                transactionId,
                guildId.toString(),
                playerId.toString(),
                transactionType.name,
                serializedItem,
                slot,
                timestamp
            ) > 0
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to log item transaction for guild $guildId", e)
        }
    }

    /**
     * Retrieves transactions for a guild within a time range.
     *
     * @param guildId The ID of the guild.
     * @param startTime The start of the time range (milliseconds since epoch), or null for no lower bound.
     * @param endTime The end of the time range (milliseconds since epoch), or null for no upper bound.
     * @param limit Maximum number of results to return (default: no limit).
     * @param offset Number of results to skip (for pagination, default: 0).
     * @return List of VaultTransaction objects.
     */
    fun getTransactions(
        guildId: UUID,
        startTime: Long? = null,
        endTime: Long? = null,
        limit: Int? = null,
        offset: Int = 0
    ): List<VaultTransaction> {
        val conditions = mutableListOf("guild_id = ?")
        val params = mutableListOf<Any>(guildId.toString())

        if (startTime != null) {
            conditions.add("timestamp >= ?")
            params.add(startTime)
        }

        if (endTime != null) {
            conditions.add("timestamp <= ?")
            params.add(endTime)
        }

        var sql = """
            SELECT id, guild_id, player_id, transaction_type, amount, item_data, slot, timestamp
            FROM vault_transaction_log
            WHERE ${conditions.joinToString(" AND ")}
            ORDER BY timestamp DESC
        """.trimIndent()

        if (limit != null) {
            sql += " LIMIT $limit OFFSET $offset"
        }

        return try {
            val results = storage.connection.getResults(sql, *params.toTypedArray())
            results.map { row ->
                VaultTransaction(
                    id = row.getString("id"),
                    guildId = UUID.fromString(row.getString("guild_id")),
                    playerId = UUID.fromString(row.getString("player_id")),
                    transactionType = VaultTransactionType.valueOf(row.getString("transaction_type")),
                    amount = row.get("amount") as? Long,
                    itemData = row.getString("item_data"),
                    slot = row.get("slot") as? Int,
                    timestamp = row.getLong("timestamp")
                )
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to get transactions for guild $guildId", e)
        }
    }

    /**
     * Retrieves transactions for a specific player within a time range.
     *
     * @param playerId The ID of the player.
     * @param startTime The start of the time range (milliseconds since epoch), or null for no lower bound.
     * @param endTime The end of the time range (milliseconds since epoch), or null for no upper bound.
     * @param limit Maximum number of results to return (default: no limit).
     * @param offset Number of results to skip (for pagination, default: 0).
     * @return List of VaultTransaction objects.
     */
    fun getPlayerTransactions(
        playerId: UUID,
        startTime: Long? = null,
        endTime: Long? = null,
        limit: Int? = null,
        offset: Int = 0
    ): List<VaultTransaction> {
        val conditions = mutableListOf("player_id = ?")
        val params = mutableListOf<Any>(playerId.toString())

        if (startTime != null) {
            conditions.add("timestamp >= ?")
            params.add(startTime)
        }

        if (endTime != null) {
            conditions.add("timestamp <= ?")
            params.add(endTime)
        }

        var sql = """
            SELECT id, guild_id, player_id, transaction_type, amount, item_data, slot, timestamp
            FROM vault_transaction_log
            WHERE ${conditions.joinToString(" AND ")}
            ORDER BY timestamp DESC
        """.trimIndent()

        if (limit != null) {
            sql += " LIMIT $limit OFFSET $offset"
        }

        return try {
            val results = storage.connection.getResults(sql, *params.toTypedArray())
            results.map { row ->
                VaultTransaction(
                    id = row.getString("id"),
                    guildId = UUID.fromString(row.getString("guild_id")),
                    playerId = UUID.fromString(row.getString("player_id")),
                    transactionType = VaultTransactionType.valueOf(row.getString("transaction_type")),
                    amount = row.get("amount") as? Long,
                    itemData = row.getString("item_data"),
                    slot = row.get("slot") as? Int,
                    timestamp = row.getLong("timestamp")
                )
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to get transactions for player $playerId", e)
        }
    }

    /**
     * Archives old transaction log entries to prevent database bloat.
     * Deletes entries older than the specified timestamp.
     *
     * @param olderThan Timestamp (milliseconds since epoch) - entries older than this will be deleted.
     * @return The number of entries deleted.
     */
    fun archiveOldTransactions(olderThan: Long): Int {
        val sql = "DELETE FROM vault_transaction_log WHERE timestamp < ?"

        return try {
            storage.connection.executeUpdate(sql, olderThan)
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to archive old transactions", e)
        }
    }

    /**
     * Gets the total number of transaction log entries.
     *
     * @return The total count of transaction log entries.
     */
    fun getTransactionCount(): Long {
        val sql = "SELECT COUNT(*) as count FROM vault_transaction_log"

        return try {
            val results = storage.connection.getResults(sql)
            if (results.isEmpty()) 0L else results[0].getInt("count").toLong()
        } catch (e: SQLException) {
            0L
        }
    }

    /**
     * Gets the number of transactions for a specific guild.
     *
     * @param guildId The guild ID.
     * @return The count of transactions.
     */
    fun getGuildTransactionCount(guildId: UUID): Long {
        val sql = "SELECT COUNT(*) as count FROM vault_transaction_log WHERE guild_id = ?"

        return try {
            val results = storage.connection.getResults(sql, guildId.toString())
            if (results.isEmpty()) 0L else results[0].getInt("count").toLong()
        } catch (e: SQLException) {
            0L
        }
    }

    /**
     * Gets the most recent transactions across all guilds.
     * Useful for admin monitoring and crash recovery analysis.
     *
     * @param limit Maximum number of results (default: 100).
     * @return List of recent VaultTransaction objects.
     */
    fun getRecentTransactions(limit: Int = 100): List<VaultTransaction> {
        val sql = """
            SELECT id, guild_id, player_id, transaction_type, amount, item_data, slot, timestamp
            FROM vault_transaction_log
            ORDER BY timestamp DESC
            LIMIT $limit
        """.trimIndent()

        return try {
            val results = storage.connection.getResults(sql)
            results.map { row ->
                VaultTransaction(
                    id = row.getString("id"),
                    guildId = UUID.fromString(row.getString("guild_id")),
                    playerId = UUID.fromString(row.getString("player_id")),
                    transactionType = VaultTransactionType.valueOf(row.getString("transaction_type")),
                    amount = row.get("amount") as? Long,
                    itemData = row.getString("item_data"),
                    slot = row.get("slot") as? Int,
                    timestamp = row.getLong("timestamp")
                )
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to get recent transactions", e)
        }
    }

    /**
     * Gets transactions in the time window before a crash.
     * Useful for investigating data loss after server crashes.
     *
     * @param crashTime The timestamp of the crash (milliseconds since epoch).
     * @param windowSeconds The number of seconds before the crash to retrieve (default: 5).
     * @return List of VaultTransaction objects from the window before the crash.
     */
    fun getTransactionsBeforeCrash(crashTime: Long, windowSeconds: Int = 5): List<VaultTransaction> {
        val startTime = crashTime - (windowSeconds * 1000)

        val sql = """
            SELECT id, guild_id, player_id, transaction_type, amount, item_data, slot, timestamp
            FROM vault_transaction_log
            WHERE timestamp >= ? AND timestamp <= ?
            ORDER BY timestamp DESC
        """.trimIndent()

        return try {
            val results = storage.connection.getResults(sql, startTime, crashTime)
            results.map { row ->
                VaultTransaction(
                    id = row.getString("id"),
                    guildId = UUID.fromString(row.getString("guild_id")),
                    playerId = UUID.fromString(row.getString("player_id")),
                    transactionType = VaultTransactionType.valueOf(row.getString("transaction_type")),
                    amount = row.get("amount") as? Long,
                    itemData = row.getString("item_data"),
                    slot = row.get("slot") as? Int,
                    timestamp = row.getLong("timestamp")
                )
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to get transactions before crash", e)
        }
    }

    /**
     * Gets transactions of a specific type for a guild.
     *
     * @param guildId The guild ID.
     * @param transactionType The type of transaction to filter by.
     * @param limit Maximum number of results (default: 50).
     * @return List of VaultTransaction objects.
     */
    fun getTransactionsByType(
        guildId: UUID,
        transactionType: VaultTransactionType,
        limit: Int = 50
    ): List<VaultTransaction> {
        val sql = """
            SELECT id, guild_id, player_id, transaction_type, amount, item_data, slot, timestamp
            FROM vault_transaction_log
            WHERE guild_id = ? AND transaction_type = ?
            ORDER BY timestamp DESC
            LIMIT $limit
        """.trimIndent()

        return try {
            val results = storage.connection.getResults(sql, guildId.toString(), transactionType.name)
            results.map { row ->
                VaultTransaction(
                    id = row.getString("id"),
                    guildId = UUID.fromString(row.getString("guild_id")),
                    playerId = UUID.fromString(row.getString("player_id")),
                    transactionType = VaultTransactionType.valueOf(row.getString("transaction_type")),
                    amount = row.get("amount") as? Long,
                    itemData = row.getString("item_data"),
                    slot = row.get("slot") as? Int,
                    timestamp = row.getLong("timestamp")
                )
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to get transactions by type for guild $guildId", e)
        }
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
}
