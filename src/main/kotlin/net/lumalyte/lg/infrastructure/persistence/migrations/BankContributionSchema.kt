package net.lumalyte.lg.infrastructure.persistence.migrations

import java.sql.Connection

/** Retained anti-recycling state. Never reset by daily caps or prestige. */
object BankContributionSchema {
    const val VERSION = 48
    const val ACCOUNTS = "guild_bank_contribution_accounts"
    const val RECEIPTS = "guild_bank_contribution_receipts"
    const val GUARDS = "guild_bank_contribution_guards"
    const val QUESTS = "guild_bank_contribution_quests"

    fun create(connection: Connection, mariaDb: Boolean) {
        val engine = if (mariaDb) " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci" else ""
        connection.createStatement().use { s ->
            s.execute("CREATE TABLE IF NOT EXISTS $GUARDS (guild_id VARCHAR(36) PRIMARY KEY)$engine")
            s.execute("""
                CREATE TABLE IF NOT EXISTS $ACCOUNTS (
                    guild_id VARCHAR(36) PRIMARY KEY,
                    withdrawal_debt BIGINT NOT NULL DEFAULT 0,
                    fresh_total BIGINT NOT NULL DEFAULT 0
                )$engine
            """.trimIndent())
            s.execute("""
                CREATE TABLE IF NOT EXISTS $RECEIPTS (
                    transaction_id VARCHAR(36) PRIMARY KEY,
                    guild_id VARCHAR(36) NOT NULL,
                    fresh_before BIGINT NOT NULL,
                    fresh_after BIGINT NOT NULL
                )$engine
            """.trimIndent())
            s.execute("""
                CREATE TABLE IF NOT EXISTS $QUESTS (
                    transaction_id VARCHAR(36) NOT NULL,
                    quest_id VARCHAR(255) NOT NULL,
                    week_id VARCHAR(255) NOT NULL,
                    PRIMARY KEY (transaction_id, quest_id)
                )$engine
            """.trimIndent())
        }
    }
}
