package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.persistence.BankDepositXpRepository
import net.lumalyte.lg.application.services.ChapterTwoGuildAwardRules
import net.lumalyte.lg.infrastructure.persistence.migrations.BankContributionSchema
import net.lumalyte.lg.domain.entities.BankDepositXpCompletion
import net.lumalyte.lg.domain.entities.BankDepositXpEligibility
import net.lumalyte.lg.domain.entities.BankDepositXpStatus
import net.lumalyte.lg.domain.entities.ExperienceAwardRequest
import net.lumalyte.lg.domain.entities.ExperienceAwardResult
import net.lumalyte.lg.domain.values.ExperienceSource
import net.lumalyte.lg.infrastructure.persistence.migrations.BankDepositXpSchema
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import java.sql.Connection
import java.time.Instant
import java.util.UUID

/** Ordered, retained delivery receipts; no currency/provider action runs in this consumer. */
class BankDepositXpRepositorySQL(
    private val storage: Storage<Database>,
    private val awards: ExperienceAwardRepositorySQL,
    private val clock: () -> Instant = { Instant.now() },
) : BankDepositXpRepository {
    private val ledger = BankDepositXpLedger(storage.dialect == SqlDialect.MARIADB)
    private val highWater = BankProgressionRepositorySQL(storage, awards)

    init {
        storage.connection.connection.use { BankContributionSchema.create(it, storage.dialect == SqlDialect.MARIADB) }
        storage.connection.connection.use { BankDepositXpSchema.create(it, storage.dialect == SqlDialect.MARIADB) }
    }

    override fun complete(transactionId: UUID): BankDepositXpCompletion? {
        // Immutable identity lookup and attempt bookkeeping never share the award transaction snapshot.
        val guildId = storage.connection.connection.use { c ->
            c.selectOne("SELECT guild_id FROM guild_bank_xp_deliveries WHERE transaction_id = ?", transactionId.toString()) {
                UUID.fromString(it.getString(1))
            }
        } ?: return null
        recordAttempt(transactionId)
        return storage.connection.connection.use { c ->
            c.committingTransaction {
                val run = ledger.fence(c, guildId)
                val receipt = checkNotNull(ledger.read(c, transactionId)) { "Deposit delivery disappeared" }
                check(receipt.mutation.guildId == guildId)
                if (receipt.status != BankDepositXpStatus.READY) return@committingTransaction receipt.completion()
                validateCanonical(c, receipt)
                val earlier = c.selectOne(
                    "SELECT 1 FROM guild_bank_xp_deliveries WHERE guild_id = ? AND status = 'READY' " +
                        "AND settlement_sequence < ? LIMIT 1",
                    guildId.toString(), checkNotNull(receipt.sequence),
                ) { true } ?: false
                if (earlier) return@committingTransaction receipt.completion().copy(status = BankDepositXpStatus.WAITING)
                val window = receipt.quote.policy.windowContaining(receipt.quote.occurredAt)
                // Denied, confirmed money must not become eligible growth on a later deposit.
                // A removed guild is the exception: recovery must not create orphan state.
                val contribution =
                    c.selectOne(
                        "SELECT fresh_before, fresh_after FROM guild_bank_contribution_receipts " +
                            "WHERE transaction_id = ? AND guild_id = ?",
                        transactionId.toString(),
                        guildId.toString(),
                    ) { it.getLong(1) to it.getLong(2) }
                // Legacy settled receipts without contribution provenance cannot mint fresh rewards.
                if (run.guildExists && window != null) {
                    highWater.reserve(
                        c,
                        guildId,
                        checkNotNull(receipt.oldBalance),
                        checkNotNull(receipt.newBalance),
                        receipt.quote.valuePerUnit,
                        window,
                    )
                }
                val units =
                    if (run.guildExists && window != null && contribution != null) {
                        ChapterTwoGuildAwardRules.netNewBankUnits(
                            contribution.first,
                            contribution.second,
                            receipt.quote.valuePerUnit,
                        )
                    } else {
                        0
                    }
                val denial = denial(receipt, run)
                if (denial != null) finish(c, receipt, denial) else award(c, receipt, units)
            }
        }
    }

    private fun denial(receipt: BankDepositXpReceipt, run: BankDepositXpRun): BankDepositXpStatus? = when {
        receipt.captureDenial != null -> receipt.captureDenial
        !run.guildExists || run.prestige == null || run.prestige != receipt.prestige || run.chapter?.id != receipt.chapterId ->
            BankDepositXpStatus.STALE_RUN
        run.chapter?.allows(clock().toEpochMilli()) == false -> BankDepositXpStatus.CHAPTER_FROZEN
        receipt.quote.eligibility == BankDepositXpEligibility.AFK -> BankDepositXpStatus.AFK
        receipt.quote.policy.windowContaining(receipt.quote.occurredAt) == null -> BankDepositXpStatus.UNLIMITED
        else -> null
    }

    private fun award(c: Connection, receipt: BankDepositXpReceipt, units: Int): BankDepositXpCompletion {
        val quote = receipt.quote
        if (units == 0) return finish(c, receipt, BankDepositXpStatus.NO_UNITS)
        if (!quote.policy.enabled || quote.eligibility == BankDepositXpEligibility.DISABLED) {
            return finish(c, receipt, BankDepositXpStatus.DISABLED)
        }
        if (quote.eligibility == BankDepositXpEligibility.INVALID_UNITS) {
            return finish(c, receipt, BankDepositXpStatus.INVALID_UNITS)
        }
        val requested = quote.requestedXp(units) ?: return finish(c, receipt, BankDepositXpStatus.INVALID_UNITS)
        val request = ExperienceAwardRequest(
            receipt.mutation.guildId, receipt.mutation.actorId, ExperienceSource.BANK_DEPOSIT, units,
            quote.occurredAt, transactionId = receipt.mutation.transactionId,
        )
        return when (val result = awards.awardInTransaction(c, request, quote.policy, requested)) {
            is ExperienceAwardResult.Awarded -> finish(c, receipt, BankDepositXpStatus.AWARDED, result.acceptedXp)
                .copy(awardedNow = true, leveledUpTo = result.leveledUpTo)
            is ExperienceAwardResult.NoAllowance -> finish(c, receipt, BankDepositXpStatus.CAPPED)
            else -> error("Unsettled bank delivery has an unexpected XP receipt/result: $result")
        }
    }

    private fun finish(c: Connection, receipt: BankDepositXpReceipt, status: BankDepositXpStatus, xp: Int = 0): BankDepositXpCompletion {
        check(c.updateStatement(
            "UPDATE guild_bank_xp_deliveries SET status = ?, accepted_xp = ?, cache_pending = ? " +
                "WHERE transaction_id = ? AND status = 'READY'",
            status.name, xp, if (status == BankDepositXpStatus.AWARDED) 1 else 0, receipt.mutation.transactionId.toString(),
        ) == 1)
        return receipt.completion().copy(status = status, acceptedXp = xp, cachePending = status == BankDepositXpStatus.AWARDED)
    }

    /** This nonlocking read follows the locked READY receipt; it cannot precede that receipt's commit. */
    private fun validateCanonical(c: Connection, receipt: BankDepositXpReceipt) {
        val matches = c.selectOne(
            "SELECT o.*, e.phase AS external_phase, e.purpose AS external_purpose FROM guild_gold_operations o " +
                "JOIN guild_gold_external_attempts e ON e.transaction_id = o.transaction_id WHERE o.transaction_id = ?",
            receipt.mutation.transactionId.toString(),
        ) { row ->
            val mutation = receipt.mutation
            row.getString("status") == "APPLIED" && row.getString("external_phase") == "CONFIRMED" &&
                row.getString("external_purpose") == "DEPOSIT" && row.getString("direction") == "CREDIT" &&
                row.getString("guild_id") == mutation.guildId.toString() && row.getString("actor_id") == mutation.actorId.toString() &&
                row.getString("route") == mutation.route.name && row.getLong("amount") == mutation.amount &&
                row.getLong("fee") == mutation.fee && row.getString("description") == mutation.description &&
                row.getObject("old_balance") != null && row.getLong("old_balance") == receipt.oldBalance &&
                row.getObject("new_balance") != null && row.getLong("new_balance") == receipt.newBalance
        } ?: false
        check(matches) { "READY deposit delivery disagrees with canonical confirmed credit" }
    }

    override fun pending(limit: Int): List<UUID> = ids(
        "SELECT d.transaction_id FROM guild_bank_xp_deliveries d WHERE d.status = 'READY' " +
            "AND NOT EXISTS (SELECT 1 FROM guild_bank_xp_deliveries p WHERE p.guild_id = d.guild_id " +
            "AND p.status = 'READY' AND p.settlement_sequence < d.settlement_sequence) " +
            "ORDER BY d.last_attempt_at, d.guild_id LIMIT ?", limit,
    )

    override fun pendingCache(limit: Int): List<UUID> = ids(
        "SELECT transaction_id FROM guild_bank_xp_deliveries WHERE status = 'AWARDED' AND cache_pending = 1 " +
            "ORDER BY cache_attempt_at, guild_id, settlement_sequence LIMIT ?", limit,
    )

    override fun acknowledgeCache(transactionId: UUID) {
        storage.connection.executeUpdate(
            "UPDATE guild_bank_xp_deliveries SET cache_pending = 0 WHERE transaction_id = ? AND status = 'AWARDED'",
            transactionId.toString(),
        )
    }

    private fun ids(sql: String, limit: Int): List<UUID> {
        require(limit in 1..MAX_BATCH)
        return storage.connection.connection.use { c ->
            c.prepareStatement(sql).use { statement ->
                statement.setInt(1, limit)
                statement.executeQuery().use { rows -> buildList { while (rows.next()) add(UUID.fromString(rows.getString(1))) } }
            }
        }
    }

    private fun recordAttempt(id: UUID) {
        val at = clock().toEpochMilli()
        // Attempt metadata commits even if the later award rolls back, preventing a poison head
        // from monopolizing every bounded page. It never acknowledges delivery or publication.
        for ((column, status) in listOf("last_attempt_at" to "READY", "cache_attempt_at" to "AWARDED")) {
            storage.connection.executeUpdate(
                "UPDATE guild_bank_xp_deliveries SET $column = CASE WHEN $column >= ? THEN $column + 1 ELSE ? END " +
                    "WHERE transaction_id = ? AND status = ?", at, at, id.toString(), status,
            )
        }
    }

    private fun BankDepositXpReceipt.completion() = BankDepositXpCompletion(
        mutation.transactionId, mutation.guildId, status, acceptedXp, cachePending = cachePending,
    )

    companion object {
        const val MAX_BATCH = 128
    }
}
