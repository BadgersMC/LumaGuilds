package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.domain.entities.BankDepositXpEligibility
import net.lumalyte.lg.domain.entities.BankDepositXpQuote
import net.lumalyte.lg.domain.entities.BankDepositXpStatus
import net.lumalyte.lg.domain.gold.GuildGoldDirection
import net.lumalyte.lg.domain.gold.GuildGoldMutation
import net.lumalyte.lg.domain.gold.GuildGoldRoute
import net.lumalyte.lg.domain.values.CapPeriod
import net.lumalyte.lg.domain.values.ExperiencePolicy
import net.lumalyte.lg.domain.values.ExperienceSource
import java.sql.Connection
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

/** All methods use a caller-owned connection. Settlement never takes lifecycle/guild/account locks. */
internal class BankDepositXpLedger(private val maria: Boolean) {
    private val lock = if (maria) " FOR UPDATE" else ""

    /** Same order as shop XP and rollover: lifecycle, guild, reward account. */
    fun fence(c: Connection, guildId: UUID): BankDepositXpRun {
        if (!maria) c.updateStatement("UPDATE guild_bank_xp_deliveries SET status = status WHERE 0")
        val chapter = c.selectOne(
            "SELECT chapter_id, phase, starts_at, ends_at FROM chapter_lifecycle " +
                "ORDER BY CASE WHEN phase = 'COMPLETE' THEN 1 ELSE 0 END, starts_at DESC LIMIT 1$lock",
        ) { Chapter(it.getString(1), it.getString(2), it.nullableLong("starts_at"), it.nullableLong("ends_at")) }
        val exists = c.selectOne("SELECT 1 FROM guilds WHERE id = ?$lock", guildId.toString()) { true } ?: false
        val prestige = c.selectOne(
            "SELECT prestige_count FROM guild_reward_accounts WHERE guild_id = ?$lock", guildId.toString(),
        ) { it.getInt(1) }
        return BankDepositXpRun(exists, prestige, chapter)
    }

    fun capture(c: Connection, mutation: GuildGoldMutation, quote: BankDepositXpQuote, run: BankDepositXpRun) {
        requireDeposit(mutation)
        val denial = when {
            !run.guildExists || run.prestige == null -> BankDepositXpStatus.STALE_RUN
            run.chapter?.allows(quote.occurredAt.toEpochMilli()) == false -> BankDepositXpStatus.CHAPTER_FROZEN
            else -> null
        }
        check(c.updateStatement(
            """INSERT INTO guild_bank_xp_deliveries
               (transaction_id, guild_id, actor_id, route, amount, fee, description, occurred_at,
                source_pool, award_xp, cap_xp, cap_period, policy_enabled, boost_multiplier,
                eligibility, value_per_unit, chapter_id, prestige_count, capture_denial, status)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PREPARED')""",
            mutation.transactionId.toString(), mutation.guildId.toString(), mutation.actorId.toString(),
            mutation.route.name, mutation.amount, mutation.fee, mutation.description, quote.occurredAt.toEpochMilli(),
            quote.policy.pool, quote.policy.awardXp, quote.policy.capXp, quote.policy.period.name,
            if (quote.policy.enabled) 1 else 0, quote.boostMultiplier, quote.eligibility.name,
            quote.valuePerUnit, run.chapter?.id, run.prestige, denial?.name,
        ) == 1)
    }

    /** Canonical APPLIED and its ordered delivery become visible in the same commit. */
    fun settle(c: Connection, mutation: GuildGoldMutation, oldBalance: Long, newBalance: Long) {
        // A missing intent denotes an older or non-deposit operation; never synthesize provenance.
        val receipt = read(c, mutation.transactionId, forUpdate = false) ?: return
        requireDeposit(mutation)
        check(receipt.mutation == mutation) { "Deposit delivery fingerprint disagrees with canonical gold" }
        check(oldBalance >= 0 && newBalance == Math.addExact(oldBalance, mutation.amount))
        if (receipt.status != BankDepositXpStatus.PREPARED) {
            check(receipt.oldBalance == oldBalance && receipt.newBalance == newBalance && receipt.sequence != null) {
                "Deposit delivery settlement changed"
            }
            return
        }
        val confirmed = c.selectOne(
            "SELECT phase, purpose FROM guild_gold_external_attempts WHERE transaction_id = ?",
            mutation.transactionId.toString(),
        ) { it.getString(1) == "CONFIRMED" && it.getString(2) == "DEPOSIT" } ?: false
        check(confirmed) { "Deposit delivery requires confirmed external consumption" }
        val suffix = if (maria) "ON DUPLICATE KEY UPDATE last_sequence = last_sequence + 1"
            else "ON CONFLICT(guild_id) DO UPDATE SET last_sequence = last_sequence + 1"
        c.updateStatement(
            "INSERT INTO guild_bank_xp_delivery_sequences (guild_id, last_sequence) VALUES (?, 1) $suffix",
            mutation.guildId.toString(),
        )
        val sequence = checkNotNull(c.selectOne(
            "SELECT last_sequence FROM guild_bank_xp_delivery_sequences WHERE guild_id = ?$lock",
            mutation.guildId.toString(),
        ) { it.getLong(1) }).also { check(it > 0) }
        check(c.updateStatement(
            "UPDATE guild_bank_xp_deliveries SET old_balance = ?, new_balance = ?, settlement_sequence = ?, " +
                "status = 'READY' WHERE transaction_id = ? AND status = 'PREPARED'",
            oldBalance, newBalance, sequence, mutation.transactionId.toString(),
        ) == 1)
    }

    fun cancel(c: Connection, transactionId: UUID) {
        val receipt = read(c, transactionId, forUpdate = false) ?: return
        if (receipt.status == BankDepositXpStatus.CANCELLED) return
        check(receipt.status == BankDepositXpStatus.PREPARED) { "A settled deposit cannot be silently cancelled" }
        check(c.updateStatement(
            "UPDATE guild_bank_xp_deliveries SET status = 'CANCELLED' WHERE transaction_id = ? AND status = 'PREPARED'",
            transactionId.toString(),
        ) == 1)
    }

    fun read(c: Connection, id: UUID, forUpdate: Boolean = true): BankDepositXpReceipt? = c.selectOne(
        "SELECT * FROM guild_bank_xp_deliveries WHERE transaction_id = ?${if (forUpdate) lock else ""}",
        id.toString(), mapper = ::readReceipt,
    )

    private fun readReceipt(row: ResultSet): BankDepositXpReceipt {
        val enabled = row.getInt("policy_enabled").also { check(it in 0..1) }
        val pending = row.getInt("cache_pending").also { check(it in 0..1) }
        val policy = ExperiencePolicy(
            ExperienceSource.BANK_DEPOSIT, row.getString("source_pool"), row.getInt("award_xp"),
            row.getInt("cap_xp"), CapPeriod.valueOf(row.getString("cap_period")), enabled == 1,
        )
        val receipt = BankDepositXpReceipt(
            GuildGoldMutation(
                UUID.fromString(row.getString("transaction_id")), UUID.fromString(row.getString("guild_id")),
                UUID.fromString(row.getString("actor_id")), GuildGoldRoute.valueOf(row.getString("route")),
                GuildGoldDirection.CREDIT, row.getLong("amount"), row.getLong("fee"), row.getString("description"),
            ),
            BankDepositXpQuote(Instant.ofEpochMilli(row.getLong("occurred_at")), policy,
                row.getDouble("boost_multiplier"), BankDepositXpEligibility.valueOf(row.getString("eligibility")),
                row.getLong("value_per_unit")),
            row.getString("chapter_id"), row.nullableLong("prestige_count")?.toInt(),
            row.getString("capture_denial")?.let(BankDepositXpStatus::valueOf),
            BankDepositXpStatus.valueOf(row.getString("status")), row.nullableLong("old_balance"),
            row.nullableLong("new_balance"), row.nullableLong("settlement_sequence"),
            row.getInt("accepted_xp"), pending == 1,
        )
        requireDeposit(receipt.mutation)
        check(receipt.status != BankDepositXpStatus.WAITING)
        check(receipt.captureDenial == null || receipt.captureDenial in setOf(
            BankDepositXpStatus.STALE_RUN, BankDepositXpStatus.CHAPTER_FROZEN,
        ))
        if (receipt.status !in setOf(BankDepositXpStatus.PREPARED, BankDepositXpStatus.CANCELLED)) {
            check(receipt.sequence != null && receipt.sequence > 0 && receipt.oldBalance != null && receipt.newBalance != null)
            check(receipt.oldBalance >= 0 && receipt.newBalance == Math.addExact(receipt.oldBalance, receipt.mutation.amount))
        }
        check(if (receipt.status == BankDepositXpStatus.AWARDED) receipt.acceptedXp > 0 else receipt.acceptedXp == 0)
        check(!receipt.cachePending || receipt.status == BankDepositXpStatus.AWARDED)
        return receipt
    }

    private fun requireDeposit(mutation: GuildGoldMutation) {
        require(mutation.direction == GuildGoldDirection.CREDIT && mutation.route in setOf(
            GuildGoldRoute.PERSONAL_ACCOUNT, GuildGoldRoute.PHYSICAL_ITEM,
        ))
        require(mutation.amount > 0 && mutation.fee >= 0)
    }

    internal data class Chapter(val id: String, val phase: String, val startsAt: Long?, val endsAt: Long?) {
        fun allows(at: Long) = phase == "SCHEDULED" && (startsAt == null || at >= startsAt) && (endsAt == null || at < endsAt)
    }
}

internal data class BankDepositXpRun(val guildExists: Boolean, val prestige: Int?, val chapter: BankDepositXpLedger.Chapter?)

internal data class BankDepositXpReceipt(
    val mutation: GuildGoldMutation,
    val quote: BankDepositXpQuote,
    val chapterId: String?,
    val prestige: Int?,
    val captureDenial: BankDepositXpStatus?,
    val status: BankDepositXpStatus,
    val oldBalance: Long?,
    val newBalance: Long?,
    val sequence: Long?,
    val acceptedXp: Int,
    val cachePending: Boolean,
)

private fun ResultSet.nullableLong(column: String): Long? = getLong(column).let { if (wasNull()) null else it }
