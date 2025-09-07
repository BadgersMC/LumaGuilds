package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.BankRepository
import net.lumalyte.lg.domain.entities.BankAudit
import net.lumalyte.lg.domain.entities.BankTransaction
import net.lumalyte.lg.domain.entities.AuditAction
import net.lumalyte.lg.domain.entities.TransactionType
import net.lumalyte.lg.infrastructure.persistence.storage.SQLiteStorage
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.time.Instant
import java.util.UUID

class BankRepositorySQLite(private val storage: SQLiteStorage) : BankRepository {

    private val logger = LoggerFactory.getLogger(BankRepositorySQLite::class.java)

    private val transactions: MutableMap<UUID, BankTransaction> = mutableMapOf()
    private val audits: MutableMap<UUID, BankAudit> = mutableMapOf()
    private val guildBalances: MutableMap<UUID, Int> = mutableMapOf()

    init {
        createBankTables()
        preload()
    }

    private fun createBankTables() {
        val transactionsTableSql = """
            CREATE TABLE IF NOT EXISTS bank_transactions (
                id TEXT PRIMARY KEY,
                guild_id TEXT NOT NULL,
                actor_id TEXT NOT NULL,
                type TEXT NOT NULL,
                amount INTEGER NOT NULL,
                description TEXT,
                fee INTEGER NOT NULL DEFAULT 0,
                timestamp TEXT NOT NULL
            )
        """.trimIndent()

        val auditTableSql = """
            CREATE TABLE IF NOT EXISTS bank_audit (
                id TEXT PRIMARY KEY,
                transaction_id TEXT,
                guild_id TEXT NOT NULL,
                actor_id TEXT NOT NULL,
                action TEXT NOT NULL,
                details TEXT NOT NULL,
                old_balance INTEGER,
                new_balance INTEGER,
                timestamp TEXT NOT NULL
            )
        """.trimIndent()

        // Create indices for performance
        val transactionIndexSql = "CREATE INDEX IF NOT EXISTS idx_bank_transactions_guild ON bank_transactions(guild_id)"
        val auditIndexSql = "CREATE INDEX IF NOT EXISTS idx_bank_audit_guild ON bank_audit(guild_id)"
        val auditActorIndexSql = "CREATE INDEX IF NOT EXISTS idx_bank_audit_actor ON bank_audit(actor_id)"

        try {
            storage.connection.executeUpdate(transactionsTableSql)
            storage.connection.executeUpdate(auditTableSql)
            storage.connection.executeUpdate(transactionIndexSql)
            storage.connection.executeUpdate(auditIndexSql)
            storage.connection.executeUpdate(auditActorIndexSql)
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to create bank tables", e)
        }
    }

    private fun preload() {
        preloadTransactions()
        preloadAudits()
        preloadBalances()
    }

    private fun preloadTransactions() {
        val sql = """
            SELECT id, guild_id, actor_id, type, amount, description, fee, timestamp
            FROM bank_transactions
            ORDER BY timestamp DESC
        """.trimIndent()

        try {
            val results = storage.connection.getResults(sql)
            for (result in results) {
                val transaction = mapResultSetToTransaction(result)
                transactions[transaction.id] = transaction
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to preload bank transactions", e)
        }
    }

    private fun preloadAudits() {
        val sql = """
            SELECT id, transaction_id, guild_id, actor_id, action, details, old_balance, new_balance, timestamp
            FROM bank_audit
            ORDER BY timestamp DESC
        """.trimIndent()

        try {
            val results = storage.connection.getResults(sql)
            for (result in results) {
                val audit = mapResultSetToAudit(result)
                audits[audit.id] = audit
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to preload bank audits", e)
        }
    }

    private fun preloadBalances() {
        // Calculate balances from transactions
        val balanceSql = """
            SELECT guild_id,
                   SUM(CASE WHEN type = 'DEPOSIT' THEN amount ELSE -amount - fee END) as balance
            FROM bank_transactions
            GROUP BY guild_id
        """.trimIndent()

        try {
            val results = storage.connection.getResults(balanceSql)
            for (result in results) {
                val guildId = UUID.fromString(result.getString("guild_id"))
                val balance = result.getInt("balance")
                guildBalances[guildId] = balance
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to preload guild balances", e)
        }
    }

    private fun mapResultSetToTransaction(rs: co.aikar.idb.DbRow): BankTransaction {
        return BankTransaction(
            id = UUID.fromString(rs.getString("id")),
            guildId = UUID.fromString(rs.getString("guild_id")),
            actorId = UUID.fromString(rs.getString("actor_id")),
            type = TransactionType.valueOf(rs.getString("type")),
            amount = rs.getInt("amount"),
            description = rs.getString("description"),
            fee = rs.getInt("fee"),
            timestamp = Instant.parse(rs.getString("timestamp"))
        )
    }

    private fun mapResultSetToAudit(rs: co.aikar.idb.DbRow): BankAudit {
        return BankAudit(
            id = UUID.fromString(rs.getString("id")),
            transactionId = rs.getString("transaction_id")?.let { UUID.fromString(it) },
            guildId = UUID.fromString(rs.getString("guild_id")),
            actorId = UUID.fromString(rs.getString("actor_id")),
            action = AuditAction.valueOf(rs.getString("action")),
            details = rs.getString("details"),
            oldBalance = when (val value = rs.get<Any?>("old_balance")) {
                null -> 0
                is Int -> value
                is String -> value.toIntOrNull() ?: 0
                else -> value.toString().toIntOrNull() ?: 0
            },
            newBalance = when (val value = rs.get<Any?>("new_balance")) {
                null -> 0
                is Int -> value
                is String -> value.toIntOrNull() ?: 0
                else -> value.toString().toIntOrNull() ?: 0
            },
            timestamp = Instant.parse(rs.getString("timestamp"))
        )
    }

    override fun recordTransaction(transaction: BankTransaction): Boolean {
        val sql = """
            INSERT INTO bank_transactions (id, guild_id, actor_id, type, amount, description, fee, timestamp)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        return try {
            val rowsAffected = storage.connection.executeUpdate(sql,
                transaction.id.toString(),
                transaction.guildId.toString(),
                transaction.actorId.toString(),
                transaction.type.name,
                transaction.amount,
                transaction.description,
                transaction.fee,
                transaction.timestamp.toString()
            )

            if (rowsAffected > 0) {
                transactions[transaction.id] = transaction
                updateCachedBalance(transaction.guildId)
                true
            } else {
                false
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to record bank transaction", e)
        }
    }

    override fun getTransactionsForGuild(guildId: UUID, limit: Int?): List<BankTransaction> {
        var sql = """
            SELECT id, guild_id, actor_id, type, amount, description, fee, timestamp
            FROM bank_transactions
            WHERE guild_id = ?
            ORDER BY timestamp DESC
        """.trimIndent()

        if (limit != null) {
            sql += " LIMIT ?"
        }

        return try {
            val params = mutableListOf<Any>(guildId.toString())
            if (limit != null) {
                params.add(limit)
            }

            val results = storage.connection.getResults(sql, *params.toTypedArray())
            results.map { mapResultSetToTransaction(it) }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to get transactions for guild", e)
        }
    }

    override fun getGuildBalance(guildId: UUID): Int {
        // First check cache
        guildBalances[guildId]?.let { return it }

        // Calculate balance from database if not in cache
        return calculateGuildBalance(guildId)
    }

    override fun getPlayerTotalDeposits(playerId: UUID, guildId: UUID): Int {
        val sql = """
            SELECT COALESCE(SUM(amount), 0) as total
            FROM bank_transactions
            WHERE guild_id = ? AND actor_id = ? AND type = 'DEPOSIT'
        """.trimIndent()

        return try {
            val result = storage.connection.getFirstRow(sql, guildId.toString(), playerId.toString())
            result?.getInt("total") ?: 0
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to get player deposits", e)
        }
    }

    override fun getPlayerTotalWithdrawals(playerId: UUID, guildId: UUID): Int {
        val sql = """
            SELECT COALESCE(SUM(amount + fee), 0) as total
            FROM bank_transactions
            WHERE guild_id = ? AND actor_id = ? AND type = 'WITHDRAWAL'
        """.trimIndent()

        return try {
            val result = storage.connection.getFirstRow(sql, guildId.toString(), playerId.toString())
            result?.getInt("total") ?: 0
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to get player withdrawals", e)
        }
    }

    override fun recordAudit(audit: BankAudit): Boolean {
        val sql = """
            INSERT INTO bank_audit (id, transaction_id, guild_id, actor_id, action, details, old_balance, new_balance, timestamp)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        return try {
            val rowsAffected = storage.connection.executeUpdate(sql,
                audit.id.toString(),
                audit.transactionId?.toString(),
                audit.guildId.toString(),
                audit.actorId.toString(),
                audit.action.name,
                audit.details,
                audit.oldBalance,
                audit.newBalance,
                audit.timestamp.toString()
            )

            if (rowsAffected > 0) {
                audits[audit.id] = audit
                true
            } else {
                false
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to record bank audit", e)
        }
    }

    override fun getAuditForGuild(guildId: UUID, limit: Int?): List<BankAudit> {
        var sql = """
            SELECT id, transaction_id, guild_id, actor_id, action, details, old_balance, new_balance, timestamp
            FROM bank_audit
            WHERE guild_id = ?
            ORDER BY timestamp DESC
        """.trimIndent()

        if (limit != null) {
            sql += " LIMIT ?"
        }

        return try {
            val params = mutableListOf<Any>(guildId.toString())
            if (limit != null) {
                params.add(limit)
            }

            val results = storage.connection.getResults(sql, *params.toTypedArray())
            results.map { mapResultSetToAudit(it) }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to get audit for guild", e)
        }
    }

    override fun getAuditForPlayer(playerId: UUID, limit: Int?): List<BankAudit> {
        var sql = """
            SELECT id, transaction_id, guild_id, actor_id, action, details, old_balance, new_balance, timestamp
            FROM bank_audit
            WHERE actor_id = ?
            ORDER BY timestamp DESC
        """.trimIndent()

        if (limit != null) {
            sql += " LIMIT ?"
        }

        return try {
            val params = mutableListOf<Any>(playerId.toString())
            if (limit != null) {
                params.add(limit)
            }

            val results = storage.connection.getResults(sql, *params.toTypedArray())
            results.map { mapResultSetToAudit(it) }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to get audit for player", e)
        }
    }

    override fun getTransactionCountForGuild(guildId: UUID): Int {
        val sql = "SELECT COUNT(*) as count FROM bank_transactions WHERE guild_id = ?"

        return try {
            val result = storage.connection.getFirstRow(sql, guildId.toString())
            result?.getInt("count") ?: 0
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to get transaction count", e)
        }
    }

    override fun getTotalVolumeForGuild(guildId: UUID): Int {
        val sql = "SELECT COALESCE(SUM(amount), 0) as volume FROM bank_transactions WHERE guild_id = ?"

        return try {
            val result = storage.connection.getFirstRow(sql, guildId.toString())
            result?.getInt("volume") ?: 0
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to get transaction volume", e)
        }
    }

    override fun clearGuildTransactions(guildId: UUID): Boolean {
        val transactionDeleteSql = "DELETE FROM bank_transactions WHERE guild_id = ?"
        val auditDeleteSql = "DELETE FROM bank_audit WHERE guild_id = ?"

        return try {
            val transactionDeleted = storage.connection.executeUpdate(transactionDeleteSql, guildId.toString()) >= 0
            val auditDeleted = storage.connection.executeUpdate(auditDeleteSql, guildId.toString()) >= 0

            // Clear from memory
            transactions.entries.removeIf { it.value.guildId == guildId }
            audits.entries.removeIf { it.value.guildId == guildId }
            guildBalances.remove(guildId)

            transactionDeleted && auditDeleted
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to clear guild transactions", e)
        }
    }

    private fun calculateGuildBalance(guildId: UUID): Int {
        val sql = """
            SELECT COALESCE(SUM(CASE WHEN type = 'DEPOSIT' THEN amount ELSE -amount - fee END), 0) as balance
            FROM bank_transactions
            WHERE guild_id = ?
        """.trimIndent()

        return try {
            val result = storage.connection.getFirstRow(sql, guildId.toString())
            val balance = result?.getInt("balance") ?: 0

            // Update cache with calculated balance
            guildBalances[guildId] = balance
            balance
        } catch (e: SQLException) {
            logger.error("Failed to calculate guild balance for $guildId", e)
            0
        }
    }

    private fun updateCachedBalance(guildId: UUID) {
        calculateGuildBalance(guildId)
    }
}
