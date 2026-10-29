package net.lumalyte.lg.infrastructure.persistence.migrations

import java.sql.Connection

/** Retained anti-recycling state. Never reset by daily caps or prestige. */
internal object BankContributionSchema {
    /** Additive schema version for contribution provenance. */
    const val VERSION = 49

    /** Persistent guild withdrawal debt and contribution boundary. */
    const val ACCOUNTS = "guild_bank_contribution_accounts"

    /** Immutable canonical settlement receipts. */
    const val RECEIPTS = "guild_bank_contribution_receipts"

    /** Cross-instance preparation serialization. */
    const val GUARDS = "guild_bank_contribution_guards"

    /** Per-quest consumption markers retained across progress cleanup. */
    const val QUESTS = "guild_bank_contribution_quests"

    /** Captured eligible definitions for a newly prepared deposit. */
    const val QUEST_INTENTS = "guild_bank_quest_intents"
    const val QUEST_RECOVERY = "guild_bank_quest_recovery"
    const val QUEST_ATTEMPTS = "guild_bank_quest_attempts"
    private const val ATTEMPTS_SQL = "CREATE TABLE IF NOT EXISTS $QUEST_ATTEMPTS (transaction_id VARCHAR(36) PRIMARY KEY, attempts BIGINT NOT NULL)"
    const val QUEST_TRANSITION = "guild_bank_quest_transition"
    const val QUEST_REVISION = "guild_bank_quest_revision"
    private const val REVISION_SQL = "CREATE TABLE IF NOT EXISTS $QUEST_REVISION (id INT PRIMARY KEY, revision BIGINT NOT NULL)"
    private const val TRANSITION_SQL = "CREATE TABLE IF NOT EXISTS $QUEST_TRANSITION (id INT PRIMARY KEY, week_id VARCHAR(255) NOT NULL)"
    private const val RECOVERY_SQL = "CREATE TABLE IF NOT EXISTS $QUEST_RECOVERY (transaction_id VARCHAR(36) PRIMARY KEY)"
    private const val INTENT_SQL = """
        CREATE TABLE IF NOT EXISTS $QUEST_INTENTS (
            transaction_id VARCHAR(36) NOT NULL,
            week_id VARCHAR(255) NOT NULL,
            quest_id VARCHAR(255) NOT NULL,
            fingerprint VARCHAR(64) NOT NULL,
            PRIMARY KEY (transaction_id, week_id, quest_id)
        )
    """

    private const val GUARD_SQL = "CREATE TABLE IF NOT EXISTS $GUARDS (guild_id VARCHAR(36) PRIMARY KEY)"
    private const val ACCOUNT_SQL = """
        CREATE TABLE IF NOT EXISTS $ACCOUNTS (
            guild_id VARCHAR(36) PRIMARY KEY,
            withdrawal_debt BIGINT NOT NULL DEFAULT 0,
            fresh_total BIGINT NOT NULL DEFAULT 0
        )
    """
    private const val RECEIPT_SQL = """
        CREATE TABLE IF NOT EXISTS $RECEIPTS (
            transaction_id VARCHAR(36) PRIMARY KEY,
            guild_id VARCHAR(36) NOT NULL,
            fresh_before BIGINT NOT NULL,
            fresh_after BIGINT NOT NULL
        )
    """
    private const val QUEST_SQL = """
        CREATE TABLE IF NOT EXISTS $QUESTS (
            transaction_id VARCHAR(36) NOT NULL,
            quest_id VARCHAR(255) NOT NULL,
            week_id VARCHAR(255) NOT NULL,
            PRIMARY KEY (transaction_id, quest_id)
        )
    """

    /** Creates missing tables without replacing retained contribution state. */
    fun create(connection: Connection, mariaDb: Boolean) {
        val engine = if (mariaDb) " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci" else ""
        connection.createStatement().use { statement ->
            listOf(GUARD_SQL, ACCOUNT_SQL, RECEIPT_SQL, QUEST_SQL, INTENT_SQL, RECOVERY_SQL, ATTEMPTS_SQL, TRANSITION_SQL, REVISION_SQL).forEach { sql -> statement.execute(sql + engine) }
            val insert = if (mariaDb) "INSERT IGNORE" else "INSERT OR IGNORE"
            statement.execute("$insert INTO $QUEST_REVISION (id, revision) VALUES (1, 0)")
        }
    }
}
