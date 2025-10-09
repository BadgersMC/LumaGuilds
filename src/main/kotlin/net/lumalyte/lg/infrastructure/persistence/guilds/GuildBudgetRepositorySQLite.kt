package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.application.persistence.BudgetAnalyticsData
import net.lumalyte.lg.application.persistence.GuildBudgetRepository
import net.lumalyte.lg.domain.entities.BudgetCategory
import net.lumalyte.lg.domain.entities.GuildBudget
import net.lumalyte.lg.infrastructure.persistence.storage.SQLiteStorage
import java.time.Instant
import java.util.UUID

/**
 * SQLite implementation of GuildBudgetRepository.
 */
class GuildBudgetRepositorySQLite(
    private val storage: SQLiteStorage
) : GuildBudgetRepository {

    init {
        createTable()
    }

    private fun createTable() {
        val sql = """
            CREATE TABLE IF NOT EXISTS guild_budgets (
                id TEXT PRIMARY KEY,
                guild_id TEXT NOT NULL,
                category TEXT NOT NULL,
                allocated_amount INTEGER NOT NULL,
                spent_amount INTEGER NOT NULL DEFAULT 0,
                period_start TEXT NOT NULL,
                period_end TEXT NOT NULL,
                alerts_enabled BOOLEAN NOT NULL DEFAULT 1,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                UNIQUE(guild_id, category),
                FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
            );
        """.trimIndent()

        storage.connection.executeUpdate(sql)
    }

    override fun findByGuildId(guildId: UUID): List<GuildBudget> {
        val sql = "SELECT * FROM guild_budgets WHERE guild_id = ? ORDER BY category"

        return try {
            val results = storage.connection.getResults(sql, guildId.toString())
            results.map { mapResultSetToEntity(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun findByGuildIdAndCategory(guildId: UUID, category: BudgetCategory): GuildBudget? {
        val sql = "SELECT * FROM guild_budgets WHERE guild_id = ? AND category = ?"

        return try {
            val result = storage.connection.getFirstRow(sql, guildId.toString(), category.name)
            result?.let { mapResultSetToEntity(it) }
        } catch (e: Exception) {
            null
        }
    }

    override fun save(budget: GuildBudget): Boolean {
        val sql = """
            INSERT OR REPLACE INTO guild_budgets (
                id, guild_id, category, allocated_amount, spent_amount,
                period_start, period_end, alerts_enabled, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        return storage.connection.executeUpdate(sql,
            budget.id.toString(),
            budget.guildId.toString(),
            budget.category.name,
            budget.allocatedAmount,
            budget.spentAmount,
            budget.periodStart.toString(),
            budget.periodEnd.toString(),
            if (budget.alertsEnabled) 1 else 0,
            budget.createdAt.toString(),
            budget.updatedAt.toString()
        ) > 0
    }

    override fun updateSpentAmount(guildId: UUID, category: BudgetCategory, spentAmount: Int): Boolean {
        val sql = """
            UPDATE guild_budgets
            SET spent_amount = ?, updated_at = ?
            WHERE guild_id = ? AND category = ?
        """.trimIndent()

        return storage.connection.executeUpdate(sql,
            spentAmount,
            Instant.now().toString(),
            guildId.toString(),
            category.name
        ) > 0
    }

    override fun deleteByGuildIdAndCategory(guildId: UUID, category: BudgetCategory): Boolean {
        val sql = "DELETE FROM guild_budgets WHERE guild_id = ? AND category = ?"
        return storage.connection.executeUpdate(sql, guildId.toString(), category.name) > 0
    }

    override fun findExpiringBudgets(beforeDate: Instant): List<GuildBudget> {
        val sql = "SELECT * FROM guild_budgets WHERE period_end < ?"

        return try {
            val results = storage.connection.getResults(sql, beforeDate.toString())
            results.map { mapResultSetToEntity(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun findOverBudgetItems(): List<GuildBudget> {
        val sql = "SELECT * FROM guild_budgets WHERE spent_amount > allocated_amount"

        return try {
            val results = storage.connection.getResults(sql)
            results.map { mapResultSetToEntity(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun getBudgetAnalytics(guildId: UUID, startDate: Instant, endDate: Instant): Map<BudgetCategory, BudgetAnalyticsData> {
        val sql = """
            SELECT
                gb.category,
                gb.allocated_amount,
                gb.spent_amount,
                COUNT(bt.id) as transaction_count,
                AVG(bt.amount) as avg_amount,
                0 as alerts_triggered
            FROM guild_budgets gb
            LEFT JOIN bank_transactions bt ON gb.guild_id = bt.guild_id
                AND bt.timestamp BETWEEN ? AND ?
                AND bt.type = 'WITHDRAWAL'
            WHERE gb.guild_id = ?
            GROUP BY gb.category, gb.allocated_amount, gb.spent_amount
        """.trimIndent()

        return try {
            val results = storage.connection.getResults(sql, startDate.toString(), endDate.toString(), guildId.toString())
            val analytics = mutableMapOf<BudgetCategory, BudgetAnalyticsData>()
            
            for (rs in results) {
                val category = BudgetCategory.valueOf(rs.getString("category"))
                val allocatedAmount = rs.getInt("allocated_amount")
                val spentAmount = rs.getInt("spent_amount")
                val transactionCount = rs.getInt("transaction_count")
                val avgAmount = rs.get<Double>("avg_amount") ?: 0.0

                analytics[category] = BudgetAnalyticsData(
                    category = category,
                    allocatedAmount = allocatedAmount,
                    spentAmount = spentAmount,
                    transactionCount = transactionCount,
                    averageTransactionAmount = avgAmount,
                    alertsTriggered = 0 // TODO: Implement alert tracking
                )
            }
            analytics
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun mapResultSetToEntity(rs: co.aikar.idb.DbRow): GuildBudget {
        return GuildBudget(
            id = UUID.fromString(rs.getString("id")),
            guildId = UUID.fromString(rs.getString("guild_id")),
            category = BudgetCategory.valueOf(rs.getString("category")),
            allocatedAmount = rs.getInt("allocated_amount"),
            spentAmount = rs.getInt("spent_amount"),
            periodStart = Instant.parse(rs.getString("period_start")),
            periodEnd = Instant.parse(rs.getString("period_end")),
            alertsEnabled = rs.getInt("alerts_enabled") == 1,
            createdAt = Instant.parse(rs.getString("created_at")),
            updatedAt = Instant.parse(rs.getString("updated_at"))
        )
    }
}
