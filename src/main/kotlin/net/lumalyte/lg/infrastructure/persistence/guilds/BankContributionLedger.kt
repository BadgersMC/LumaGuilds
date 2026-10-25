package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.domain.gold.GuildGoldDirection
import net.lumalyte.lg.domain.gold.GuildGoldMutation
import net.lumalyte.lg.domain.gold.GuildGoldRoute
import net.lumalyte.lg.infrastructure.persistence.migrations.BankContributionSchema
import java.sql.Connection

/** Called only after confirmed payment, inside the canonical gold settlement transaction. */
internal class BankContributionLedger(private val mariaDb: Boolean) {
    fun settle(c: Connection, mutation: GuildGoldMutation, openingBalance: Long) {
        check(!c.autoCommit)
        if (mutation.route !in setOf(GuildGoldRoute.PHYSICAL_ITEM, GuildGoldRoute.PERSONAL_ACCOUNT)) return
        val id = mutation.transactionId.toString()
        if (c.selectOne("SELECT 1 FROM ${BankContributionSchema.RECEIPTS} WHERE transaction_id = ?", id) { true } == true) return
        val purpose = c.selectOne(
            "SELECT purpose FROM guild_gold_external_attempts WHERE transaction_id = ? AND phase = 'CONFIRMED'", id,
        ) { it.getString(1) }
        if (purpose != if (mutation.direction == GuildGoldDirection.CREDIT) "DEPOSIT" else "WITHDRAWAL") return
        val guild = mutation.guildId.toString()
        // Preparation excludes overlapping player effects; the contribution row serializes settlement.
        // Acquire no lifecycle/guild/XP or preparation locks here.
        // On upgrade recover only confirmed player withdrawal provenance, never historical rewards.
        val accountExists = c.selectOne(
            "SELECT 1 FROM ${BankContributionSchema.ACCOUNTS} WHERE guild_id = ?", guild,
        ) { true } == true
        if (!accountExists) {
            val highWaterExists = c.metaData.getTables(c.catalog, null, "guild_bank_xp_high_water", null).use { it.next() }
            val historicalHigh = if (highWaterExists) c.selectOne(
                "SELECT MAX(high_water_balance) FROM guild_bank_xp_high_water WHERE guild_id = ?", guild,
            ) { it.getLong(1) } ?: 0L else 0L
            val seed = if (mariaDb) "INSERT IGNORE" else "INSERT OR IGNORE"
            c.updateStatement("$seed INTO ${BankContributionSchema.ACCOUNTS} (guild_id, withdrawal_debt, fresh_total) VALUES (?, ?, ?)",
                guild, historicalDebt(c, guild, id), maxOf(historicalHigh, openingBalance))
        }
        val lock = if (mariaDb) " FOR UPDATE" else ""
        val (debt, before) = checkNotNull(c.selectOne(
            "SELECT withdrawal_debt, fresh_total FROM ${BankContributionSchema.ACCOUNTS} WHERE guild_id = ?$lock", guild,
        ) { it.getLong(1) to it.getLong(2) })
        val credit = mutation.direction == GuildGoldDirection.CREDIT
        val repaid = if (credit) minOf(debt, mutation.amount) else 0L
        val after = if (credit) Math.addExact(before, mutation.amount - repaid) else before
        val nextDebt = if (credit) debt - repaid else Math.addExact(debt, mutation.amount)
        c.updateStatement("UPDATE ${BankContributionSchema.ACCOUNTS} SET withdrawal_debt = ?, fresh_total = ? WHERE guild_id = ?",
            nextDebt, after, guild)
        c.updateStatement("INSERT INTO ${BankContributionSchema.RECEIPTS} (transaction_id, guild_id, fresh_before, fresh_after) VALUES (?, ?, ?, ?)",
            id, guild, before, after)
    }
    /** Existing journals are retained. Equal-time historical credits precede debits conservatively. */
    private fun historicalDebt(c: Connection, guild: String, currentId: String): Long =
        c.prepareStatement("""
            SELECT o.direction, o.amount FROM guild_gold_operations o
            JOIN guild_gold_external_attempts e ON e.transaction_id = o.transaction_id
            WHERE o.guild_id = ? AND o.transaction_id <> ? AND o.status = 'APPLIED'
              AND o.route IN ('PERSONAL_ACCOUNT', 'PHYSICAL_ITEM') AND e.phase = 'CONFIRMED'
              AND ((o.direction = 'CREDIT' AND e.purpose = 'DEPOSIT') OR (o.direction = 'DEBIT' AND e.purpose = 'WITHDRAWAL'))
            ORDER BY o.created_at, CASE WHEN o.direction = 'CREDIT' THEN 0 ELSE 1 END, o.transaction_id
        """.trimIndent()).use { statement ->
            statement.setString(1, guild)
            statement.setString(2, currentId)
            statement.executeQuery().use { rows ->
                var debt = 0L
                while (rows.next()) {
                    val amount = rows.getLong("amount")
                    check(amount > 0) { "Historical contribution has invalid amount" }
                    debt = if (rows.getString("direction") == "DEBIT") Math.addExact(debt, amount)
                        else debt - minOf(debt, amount)
                }
                debt
            }
        }
}
