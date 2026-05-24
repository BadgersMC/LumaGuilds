package net.lumalyte.lg.infrastructure.persistence.migrations

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.logger.slf4j.ComponentLogger
import java.sql.Connection

/**
 * Shared one-time consolidation that unifies the three historical guild-balance stores into a
 * single source of truth (store B: the `vault_gold.balance` column):
 *
 *  - Store A: the `bank_transactions` ledger sum (server-coin bank).
 *  - Store C: the `guilds.bank_balance` column (legacy virtual currency).
 *  - Store B: the `vault_gold.balance` column (vault gold counter) — the canonical store.
 *
 * For every guild, the ledger sum (A) and legacy column (C) are folded into the vault gold
 * balance (B), 1:1. The `guilds.bank_balance` column is then zeroed so it can no longer be
 * mistaken for a live balance. The `bank_transactions` ledger is left intact as audit/history.
 *
 * IMPORTANT: this is a one-shot migration and is NOT safe to run twice. The ledger rows are
 * retained for history, so a second pass would re-sum store A on top of the already-folded
 * balance and double-count. It must run exactly once, enforced by the schema-version guard in
 * [SQLiteMigrations] (version 21 -> 22).
 */
internal object GuildBalanceConsolidator {

    /**
     * Result of a single guild's fold, exposed for logging and testing.
     *
     * All fields are Long because the migration is one-shot and NOT idempotent: an Int
     * overflow during aggregation would silently truncate the new balance, zero out the
     * legacy column, and leave no audit trail to recover the original values. SQLite
     * INTEGER columns hold 8-byte values, so the JDBC reads, in-memory math, and prepared
     * statement bindings must all stay in 64-bit space end-to-end.
     */
    data class Fold(val guildId: String, val oldVault: Long, val ledger: Long, val legacy: Long, val newVault: Long)

    /**
     * Computes and applies the consolidation. Returns the list of guilds whose balance changed.
     */
    fun consolidate(connection: Connection, logger: ComponentLogger): List<Fold> {
        if (!tableExists(connection, "guilds") || !tableExists(connection, "vault_gold")) {
            logger.info(Component.text("  Skipping balance consolidation (required tables not present yet)"))
            return emptyList()
        }
        val now = System.currentTimeMillis()

        // Store A: ledger sum per guild (matches BankRepositorySQLite.calculateGuildBalance formula).
        val ledger = HashMap<String, Long>()
        if (tableExists(connection, "bank_transactions")) {
            val sql = "SELECT guild_id, COALESCE(SUM(CASE WHEN type='DEPOSIT' THEN amount ELSE -amount - fee END), 0) AS bal " +
                "FROM bank_transactions GROUP BY guild_id"
            connection.createStatement().use { st ->
                st.executeQuery(sql).use { rs ->
                    while (rs.next()) ledger[rs.getString("guild_id")] = rs.getLong("bal")
                }
            }
        }

        // Store B: existing vault gold balances.
        val vaultB = HashMap<String, Long>()
        connection.createStatement().use { st ->
            st.executeQuery("SELECT guild_id, balance FROM vault_gold").use { rs ->
                while (rs.next()) vaultB[rs.getString("guild_id")] = rs.getLong("balance")
            }
        }

        // Store C: legacy guilds.bank_balance, iterated as the authoritative guild set.
        val folds = ArrayList<Fold>()
        connection.createStatement().use { st ->
            st.executeQuery("SELECT id, bank_balance FROM guilds").use { rs ->
                while (rs.next()) {
                    val id = rs.getString("id")
                    val legacy = rs.getLong("bank_balance")
                    val a = ledger[id] ?: 0L
                    val oldVault = vaultB[id] ?: 0L
                    val newVault = maxOf(0L, oldVault + a + legacy)
                    if (a != 0L || legacy != 0L) folds.add(Fold(id, oldVault, a, legacy, newVault))
                }
            }
        }

        if (folds.isEmpty()) {
            logger.info(Component.text("  No legacy balances to fold; all guilds already unified."))
        } else {
            logger.info(Component.text("  Folding legacy balances for ${folds.size} guild(s):"))
            var totalFolded = 0L
            for (f in folds) {
                totalFolded += f.ledger + f.legacy
                logger.info(Component.text("    guild ${f.guildId.take(8)}: vault=${f.oldVault} + ledger=${f.ledger} + legacy=${f.legacy} -> ${f.newVault}"))
            }
            logger.info(Component.text("  Total folded into vault gold: $totalFolded across ${folds.size} guild(s)"))

            val upsert = "INSERT INTO vault_gold (guild_id, balance, last_modified) VALUES (?, ?, ?) " +
                "ON CONFLICT(guild_id) DO UPDATE SET balance = excluded.balance, last_modified = excluded.last_modified"
            connection.prepareStatement(upsert).use { ps ->
                for (f in folds) {
                    ps.setString(1, f.guildId)
                    ps.setLong(2, f.newVault)
                    ps.setLong(3, now)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
        }

        // Zero the legacy column so it cannot be mistaken for a live balance.
        connection.createStatement().use { st ->
            val zeroed = st.executeUpdate("UPDATE guilds SET bank_balance = 0 WHERE bank_balance <> 0")
            if (zeroed > 0) logger.info(Component.text("  Cleared legacy bank_balance on $zeroed guild(s)"))
        }

        return folds
    }

    private fun tableExists(connection: Connection, tableName: String): Boolean {
        connection.prepareStatement("SELECT name FROM sqlite_master WHERE type='table' AND name=?").use { ps ->
            ps.setString(1, tableName)
            ps.executeQuery().use { rs -> return rs.next() }
        }
    }
}
