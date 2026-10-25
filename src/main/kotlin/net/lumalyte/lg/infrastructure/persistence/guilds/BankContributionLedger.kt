package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.domain.gold.GuildGoldDirection
import net.lumalyte.lg.domain.gold.GuildGoldMutation
import net.lumalyte.lg.domain.gold.GuildGoldRoute
import net.lumalyte.lg.infrastructure.persistence.migrations.BankContributionSchema
import java.sql.Connection
import java.sql.ResultSet

/** Called only after confirmed payment, inside the canonical gold settlement transaction. */
internal class BankContributionLedger(private val mariaDb: Boolean) {
    fun settle(c: Connection, mutation: GuildGoldMutation, openingBalance: Long) {
        check(!c.autoCommit)
        if (!isConfirmedPlayerTransfer(c, mutation) || hasReceipt(c, mutation)) return
        val before = loadOrSeed(c, mutation, openingBalance)
        val after = before.after(mutation)
        saveAccount(c, mutation, after)
        saveReceipt(c, mutation, before.fresh, after.fresh)
    }

    private fun isConfirmedPlayerTransfer(c: Connection, mutation: GuildGoldMutation): Boolean {
        if (mutation.route !in setOf(GuildGoldRoute.PHYSICAL_ITEM, GuildGoldRoute.PERSONAL_ACCOUNT)) return false
        val purpose = c.selectOne(
            "SELECT purpose FROM guild_gold_external_attempts WHERE transaction_id = ? AND phase = 'CONFIRMED'",
            mutation.transactionId.toString(),
        ) { it.getString(1) }
        val expected = if (mutation.direction == GuildGoldDirection.CREDIT) "DEPOSIT" else "WITHDRAWAL"
        return purpose == expected
    }

    private fun hasReceipt(c: Connection, mutation: GuildGoldMutation): Boolean {
        return c.selectOne(
            "SELECT 1 FROM ${BankContributionSchema.RECEIPTS} WHERE transaction_id = ?",
            mutation.transactionId.toString(),
        ) { true } == true
    }

    private fun loadOrSeed(c: Connection, mutation: GuildGoldMutation, openingBalance: Long): Account {
        // Acquire no lifecycle/guild/XP or preparation locks during settlement.
        val existing = loadAccount(c, mutation)
        if (existing != null) return existing
        seedAccount(c, mutation, openingBalance)
        return checkNotNull(loadAccount(c, mutation))
    }

    private fun loadAccount(c: Connection, mutation: GuildGoldMutation): Account? {
        val lock = if (mariaDb) " FOR UPDATE" else ""
        return c.selectOne(
            "SELECT withdrawal_debt, fresh_total FROM ${BankContributionSchema.ACCOUNTS} WHERE guild_id = ?$lock",
            mutation.guildId.toString(),
        ) { Account(it.getLong(1), it.getLong(2)) }
    }

    private fun seedAccount(c: Connection, mutation: GuildGoldMutation, openingBalance: Long) {
        val guild = mutation.guildId.toString()
        val debt = historicalDebt(c, guild, mutation.transactionId.toString())
        val baseline = maxOf(historicalHighWater(c, guild), openingBalance)
        val insert = if (mariaDb) "INSERT IGNORE" else "INSERT OR IGNORE"
        c.updateStatement(
            "$insert INTO ${BankContributionSchema.ACCOUNTS} (guild_id, withdrawal_debt, fresh_total) VALUES (?, ?, ?)",
            guild,
            debt,
            baseline,
        )
    }

    private fun historicalHighWater(c: Connection, guild: String): Long {
        val exists = c.metaData.getTables(c.catalog, null, "guild_bank_xp_high_water", null).use { it.next() }
        if (!exists) return 0L
        return c.selectOne(
            "SELECT MAX(high_water_balance) FROM guild_bank_xp_high_water WHERE guild_id = ?",
            guild,
        ) { it.getLong(1) } ?: 0L
    }

    private fun saveAccount(c: Connection, mutation: GuildGoldMutation, account: Account) {
        c.updateStatement(
            "UPDATE ${BankContributionSchema.ACCOUNTS} SET withdrawal_debt = ?, fresh_total = ? WHERE guild_id = ?",
            account.debt,
            account.fresh,
            mutation.guildId.toString(),
        )
    }

    private fun saveReceipt(c: Connection, mutation: GuildGoldMutation, before: Long, after: Long) {
        c.updateStatement(
            "INSERT INTO ${BankContributionSchema.RECEIPTS} " +
                "(transaction_id, guild_id, fresh_before, fresh_after) VALUES (?, ?, ?, ?)",
            mutation.transactionId.toString(),
            mutation.guildId.toString(),
            before,
            after,
        )
    }

    /** Existing journals are retained. Equal-time historical credits precede debits conservatively. */
    private fun historicalDebt(c: Connection, guild: String, currentId: String): Long {
        return c.prepareStatement(HISTORICAL_SQL).use { statement ->
            statement.setString(1, guild)
            statement.setString(2, currentId)
            statement.executeQuery().use(::readHistoricalDebt)
        }
    }

    private fun readHistoricalDebt(rows: ResultSet): Long {
        var debt = 0L
        while (rows.next()) {
            val amount = rows.getLong("amount")
            check(amount > 0) { "Historical contribution has invalid amount" }
            debt = if (rows.getString("direction") == "DEBIT") {
                Math.addExact(debt, amount)
            } else {
                debt - minOf(debt, amount)
            }
        }
        return debt
    }

    private data class Account(val debt: Long, val fresh: Long) {
        fun after(mutation: GuildGoldMutation): Account {
            if (mutation.direction == GuildGoldDirection.DEBIT) {
                return copy(debt = Math.addExact(debt, mutation.amount))
            }
            val repaid = minOf(debt, mutation.amount)
            return Account(debt - repaid, Math.addExact(fresh, mutation.amount - repaid))
        }
    }

    private companion object {
        const val HISTORICAL_SQL = """
            SELECT o.direction, o.amount FROM guild_gold_operations o
            JOIN guild_gold_external_attempts e ON e.transaction_id = o.transaction_id
            WHERE o.guild_id = ? AND o.transaction_id <> ? AND o.status = 'APPLIED'
              AND o.route IN ('PERSONAL_ACCOUNT', 'PHYSICAL_ITEM') AND e.phase = 'CONFIRMED'
              AND ((o.direction = 'CREDIT' AND e.purpose = 'DEPOSIT') OR
                   (o.direction = 'DEBIT' AND e.purpose = 'WITHDRAWAL'))
            ORDER BY o.created_at, CASE WHEN o.direction = 'CREDIT' THEN 0 ELSE 1 END, o.transaction_id
        """
    }
}
