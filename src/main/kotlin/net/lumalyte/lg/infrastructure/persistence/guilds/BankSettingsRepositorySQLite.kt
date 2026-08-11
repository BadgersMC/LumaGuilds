package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.BankSettingsRepository
import net.lumalyte.lg.domain.entities.BankSettings
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.util.UUID

/**
 * SQLite-backed per-guild bank settings (REQ-010/011/031).
 * Follows the preload-into-memory pattern of the other repositories.
 */
class BankSettingsRepositorySQLite(private val storage: Storage<Database>) : BankSettingsRepository {

    private val logger = LoggerFactory.getLogger(BankSettingsRepositorySQLite::class.java)

    private val settingsByGuild: MutableMap<UUID, BankSettings> = mutableMapOf()

    init {
        createSettingsTable()
        preload()
    }

    private fun createSettingsTable() {
        val sql = """
            CREATE TABLE IF NOT EXISTS bank_settings (
                guild_id TEXT PRIMARY KEY,
                scheduled_deposits_enabled INTEGER NOT NULL DEFAULT 0,
                auto_rewards_enabled INTEGER NOT NULL DEFAULT 1,
                recurring_payments_enabled INTEGER NOT NULL DEFAULT 0,
                interest_rate REAL NOT NULL DEFAULT 0.02,
                dual_auth_threshold INTEGER NOT NULL DEFAULT 1000,
                monthly_budget INTEGER NOT NULL DEFAULT 10000,
                weekly_budget INTEGER NOT NULL DEFAULT 2500,
                daily_budget INTEGER NOT NULL DEFAULT 500,
                last_interest_accrual INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent()

        try {
            storage.connection.executeUpdate(sql)
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to create bank settings table", e)
        }
    }

    private fun preload() {
        val sql = """
            SELECT guild_id, scheduled_deposits_enabled, auto_rewards_enabled, recurring_payments_enabled,
                   interest_rate, dual_auth_threshold, monthly_budget, weekly_budget, daily_budget, last_interest_accrual
            FROM bank_settings
        """.trimIndent()

        try {
            val results = storage.connection.getResults(sql)
            for (result in results) {
                val settings = mapResultSetToSettings(result)
                settingsByGuild[settings.guildId] = settings
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to preload bank settings", e)
        }
    }

    private fun mapResultSetToSettings(rs: co.aikar.idb.DbRow): BankSettings {
        return BankSettings(
            guildId = UUID.fromString(rs.getString("guild_id")),
            scheduledDepositsEnabled = rs.getInt("scheduled_deposits_enabled") == 1,
            autoRewardsEnabled = rs.getInt("auto_rewards_enabled") == 1,
            recurringPaymentsEnabled = rs.getInt("recurring_payments_enabled") == 1,
            interestRate = when (val v = rs.get<Any?>("interest_rate")) {
                is Number -> v.toDouble()
                is String -> v.toDoubleOrNull() ?: 0.02
                else -> 0.02
            },
            dualAuthThreshold = rs.getInt("dual_auth_threshold"),
            monthlyBudget = rs.getInt("monthly_budget"),
            weeklyBudget = rs.getInt("weekly_budget"),
            dailyBudget = rs.getInt("daily_budget"),
            lastInterestAccrual = when (val v = rs.get<Any?>("last_interest_accrual")) {
                is Number -> v.toLong()
                is String -> v.toLongOrNull() ?: 0L
                else -> 0L
            }
        )
    }

    override fun getByGuildId(guildId: UUID): BankSettings? {
        return settingsByGuild[guildId]
    }

    override fun upsert(settings: BankSettings): Boolean {
        val sql = """
            INSERT OR REPLACE INTO bank_settings (
                guild_id, scheduled_deposits_enabled, auto_rewards_enabled, recurring_payments_enabled,
                interest_rate, dual_auth_threshold, monthly_budget, weekly_budget, daily_budget, last_interest_accrual
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        return try {
            val rowsAffected = storage.connection.executeUpdate(
                sql,
                settings.guildId.toString(),
                if (settings.scheduledDepositsEnabled) 1 else 0,
                if (settings.autoRewardsEnabled) 1 else 0,
                if (settings.recurringPaymentsEnabled) 1 else 0,
                settings.interestRate,
                settings.dualAuthThreshold,
                settings.monthlyBudget,
                settings.weeklyBudget,
                settings.dailyBudget,
                settings.lastInterestAccrual
            )

            if (rowsAffected > 0) {
                settingsByGuild[settings.guildId] = settings
                true
            } else {
                false
            }
        } catch (e: SQLException) {
            logger.error("Failed to upsert bank settings for guild ${settings.guildId}", e)
            false
        }
    }
}
