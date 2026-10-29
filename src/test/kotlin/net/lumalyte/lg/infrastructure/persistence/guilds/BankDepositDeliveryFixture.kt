package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.services.ExternalTransferResult
import net.lumalyte.lg.application.services.GuildGoldService
import net.lumalyte.lg.application.services.GuildGoldSettings
import net.lumalyte.lg.application.services.GuildGoldSettingsProvider
import net.lumalyte.lg.application.services.PersonalEconomyPort
import net.lumalyte.lg.application.services.PersonalGoldRequest
import net.lumalyte.lg.application.services.PhysicalCommitResult
import net.lumalyte.lg.application.services.PhysicalGoldPort
import net.lumalyte.lg.application.services.PhysicalGoldRequest
import net.lumalyte.lg.application.services.PhysicalGoldReservation
import net.lumalyte.lg.application.services.PhysicalReservationResult
import net.lumalyte.lg.domain.entities.BankDepositXpQuote
import net.lumalyte.lg.domain.gold.GuildGoldCapacity
import net.lumalyte.lg.domain.gold.GuildGoldPolicy
import net.lumalyte.lg.domain.values.CapPeriod
import net.lumalyte.lg.domain.values.ExperiencePolicy
import net.lumalyte.lg.domain.values.ExperienceSource
import net.lumalyte.lg.domain.values.ProgressionCurve
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

/** Real SQL state survives [restart]; only external payment outcomes and the clock are controlled. */
@Suppress("MagicNumber", "TooManyFunctions")
abstract class BankDepositDeliveryFixture {
    private var storage: Storage<Database>? = null
    protected val db get() = checkNotNull(storage)
    protected lateinit var gold: GuildGoldRepositorySQL
    protected lateinit var delivery: BankDepositXpRepositorySQL
    protected lateinit var service: GuildGoldService
    protected val guild: UUID = UUID.randomUUID()
    protected val actor: UUID = UUID.randomUUID()
    protected val occurredAt: Instant = Instant.parse("2026-10-20T12:00:00Z")
    protected var now: Instant = occurredAt
    protected val originalPolicy =
        ExperiencePolicy(ExperienceSource.BANK_DEPOSIT, "BANK_DEPOSIT", 1, 100, CapPeriod.DAILY, true)
    protected var quote = BankDepositXpQuote(occurredAt, originalPolicy)
    protected var moneyPolicy = GuildGoldPolicy(
        minDeposit = 1, maxDeposit = 100_000, withdrawalPercent = 1.0,
        dailyWithdrawalLimit = 100_000, depositFeePercent = 0.0, withdrawalFeePercent = 0.0,
        maxDepositFee = 1_000, maxWithdrawalFee = 1_000, globalCapacity = 100_000,
        suspiciousThreshold = 100_000, autoFreezeSuspicious = false,
    )
    protected val personal = ControlledPersonalEconomy()
    protected val physical = ControlledPhysicalGold()

    protected abstract fun newStorage(): Storage<Database>

    @BeforeEach fun setupDeliveryDatabase() {
        storage = newStorage()
        listOf(
            "quest_leaderboard_payouts", "guild_quest_weekly_bonus", "guild_quest_progress",
            "weekly_quest_definitions", "weekly_quest_sets",
            "guild_bank_quest_transition", "guild_bank_quest_recovery", "guild_bank_quest_intents",
            "guild_bank_contribution_guards", "guild_bank_contribution_quests",
            "guild_bank_contribution_receipts", "guild_bank_contribution_accounts",
            "guild_bank_xp_deliveries", "guild_bank_xp_delivery_sequences", "guild_bank_xp_high_water",
            "experience_transactions", "guild_experience_source_usage", "guild_progression",
            "guild_gold_external_attempts", "guild_gold_operations", "guild_gold_withdrawal_usage",
            "guild_gold_security", "vault_gold", "guild_reward_accounts", "chapter_lifecycle", "guilds",
        ).forEach { execute("DROP TABLE IF EXISTS $it") }
        val engine = if (db.dialect == SqlDialect.MARIADB) " ENGINE=InnoDB" else ""
        execute("CREATE TABLE guilds (id VARCHAR(36) PRIMARY KEY, level INT NOT NULL)$engine")
        execute("INSERT INTO guilds VALUES (?, 1)", guild.toString())
        execute("CREATE TABLE guild_reward_accounts (guild_id VARCHAR(36) PRIMARY KEY, prestige_count INT)$engine")
        execute("INSERT INTO guild_reward_accounts VALUES (?, 0)", guild.toString())
        execute(
            "CREATE TABLE chapter_lifecycle (chapter_id VARCHAR(64) PRIMARY KEY, " +
                "phase VARCHAR(32), starts_at BIGINT, ends_at BIGINT)$engine",
        )
        execute("INSERT INTO chapter_lifecycle VALUES ('chapter-2', 'SCHEDULED', 0, ?)", Long.MAX_VALUE)
        openRepositories()
    }

    @AfterEach fun closeDeliveryDatabase() {
        storage?.connection?.close(5, TimeUnit.SECONDS)
    }

    protected fun restart() {
        db.connection.close(5, TimeUnit.SECONDS)
        storage = newStorage()
        openRepositories()
    }

    protected fun newConsumer(): BankDepositXpRepositorySQL = BankDepositXpRepositorySQL(
        db, ExperienceAwardRepositorySQL(db, ProgressionCurve(500.0, 1.15, 150, 100)), clock = { now },
    )

    protected fun newService(
        repository: GuildGoldRepositorySQL = GuildGoldRepositorySQL(db),
        captureQuote: Boolean = true,
    ) = GuildGoldService(
        repository = repository,
        settingsProvider = GuildGoldSettingsProvider { GuildGoldSettings(moneyPolicy, GuildGoldCapacity(100_000, 0)) },
        personalEconomy = personal,
        physicalGold = physical,
        depositXpQuote = if (captureQuote) ({ _: UUID -> quote }) else null,
    )

    private fun openRepositories() {
        delivery = newConsumer()
        gold = GuildGoldRepositorySQL(db)
        service = newService(gold)
    }

    protected fun personalRequest(id: UUID = UUID.randomUUID(), amount: Long = 100) =
        PersonalGoldRequest(id, guild, actor, amount, "Durable personal bank delivery")

    protected fun physicalRequest(id: UUID = UUID.randomUUID(), amount: Long = 100) =
        PhysicalGoldRequest(id, guild, actor, amount, "Durable physical bank delivery")

    protected fun seedHighWater(balance: Long) {
        val window = checkNotNull(originalPolicy.windowContaining(occurredAt))
        execute(
            "INSERT INTO guild_bank_xp_high_water (guild_id, period_start, period_end, high_water_balance) VALUES (?, ?, ?, ?)",
            guild.toString(), window.startInclusive.toEpochMilli(), window.endExclusive.toEpochMilli(), balance,
        )
    }

    protected fun deliveryStatus(id: UUID): String? = db.connection.connection.use { connection ->
        connection.prepareStatement("SELECT status FROM guild_bank_xp_deliveries WHERE transaction_id = ?").use {
            it.setString(1, id.toString())
            it.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
        }
    }

    protected fun assertAccounting(bankBalance: Long, highWater: Long?, xp: Long, receipts: Long, usage: Long = xp) {
        assertEquals(bankBalance, gold.getBalance(guild))
        assertEquals(highWater, scalar(
            "SELECT high_water_balance FROM guild_bank_xp_high_water WHERE guild_id = ? AND period_start = ?",
            guild.toString(), checkNotNull(originalPolicy.windowContaining(occurredAt)).startInclusive.toEpochMilli(),
        ))
        assertEquals(xp, scalar("SELECT COALESCE(SUM(total_experience), 0) FROM guild_progression WHERE guild_id = ?", guild.toString()))
        assertEquals(usage, scalar("SELECT COALESCE(SUM(awarded_xp), 0) FROM guild_experience_source_usage WHERE guild_id = ?", guild.toString()))
        assertEquals(receipts, scalar("SELECT COUNT(*) FROM experience_transactions WHERE guild_id = ?", guild.toString()))
    }

    /** Use JDBC numeric conversion: SQLite can expose a small BIGINT as Integer through idb. */
    protected fun scalar(sql: String, vararg values: Any?): Long? = db.connection.connection.use { connection ->
        connection.prepareStatement(sql).use { statement ->
            values.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
            statement.executeQuery().use { rows ->
                if (!rows.next()) null else rows.getLong(1).takeUnless { rows.wasNull() }
            }
        }
    }

    protected fun execute(sql: String, vararg values: Any?) {
        db.connection.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                values.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
                statement.executeUpdate()
            }
        }
    }

    protected fun rejectInsert(table: String) {
        val body = if (db.dialect == SqlDialect.MARIADB) {
            "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'delivery insert fault'"
        } else {
            "BEGIN SELECT RAISE(ABORT, 'delivery insert fault'); END"
        }
        execute("CREATE TRIGGER reject_bank_delivery BEFORE INSERT ON $table $body")
    }

    protected fun rejectDeliveryStatus(status: String) {
        val body = if (db.dialect == SqlDialect.MARIADB) {
            "FOR EACH ROW BEGIN IF NEW.status = '$status' THEN " +
                "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'delivery status fault'; END IF; END"
        } else {
            "WHEN NEW.status = '$status' BEGIN SELECT RAISE(ABORT, 'delivery status fault'); END"
        }
        execute("CREATE TRIGGER reject_bank_delivery BEFORE UPDATE ON guild_bank_xp_deliveries $body")
    }

    protected fun allowDeliveryWrites() = execute("DROP TRIGGER reject_bank_delivery")

    protected class ControlledPersonalEconomy : PersonalEconomyPort {
        var debitCalls = 0
        var debited = 0L
        var credited = 0L
        var debitResult: ExternalTransferResult = ExternalTransferResult.Applied
        override fun isAvailable() = true
        override fun balance(playerId: UUID) = 1_000_000L - debited + credited
        override fun debit(playerId: UUID, amount: Long): ExternalTransferResult {
            debitCalls++
            if (debitResult == ExternalTransferResult.Applied) debited += amount
            return debitResult
        }
        override fun credit(playerId: UUID, amount: Long): ExternalTransferResult {
            credited += amount
            return ExternalTransferResult.Applied
        }
    }

    /** Represents the same durable external receipt store after Guilds is reconstructed. */
    protected class ControlledPhysicalGold : PhysicalGoldPort {
        private val reservations = mutableMapOf<UUID, PhysicalGoldReservation>()
        private val consumedIds = mutableSetOf<UUID>()
        private val restoredIds = mutableSetOf<UUID>()
        var reserveCalls = 0
        var commitCalls = 0
        var consumed = 0L
        var restored = 0L
        var commitResult = PhysicalCommitResult.Committed
        var consumeBeforeUnknown = false
        var deliverResult: ExternalTransferResult =
            ExternalTransferResult.Rejected("Physical withdrawal is outside this delivery fixture")
        override fun availableValue(playerId: UUID) = 1_000_000L
        override fun reserve(transactionId: UUID, playerId: UUID, requestedValue: Long): PhysicalReservationResult {
            reserveCalls++
            val reservation = reservations.getOrPut(transactionId) {
                PhysicalGoldReservation(transactionId, playerId, requestedValue)
            }
            return PhysicalReservationResult.Reserved(reservation)
        }
        override fun reservation(transactionId: UUID) = reservations[transactionId]
        override fun commit(reservation: PhysicalGoldReservation): PhysicalCommitResult {
            commitCalls++
            if (commitResult == PhysicalCommitResult.Committed || consumeBeforeUnknown) {
                if (consumedIds.add(reservation.id)) consumed += reservation.value
            }
            return commitResult
        }
        override fun restore(reservation: PhysicalGoldReservation): Boolean {
            if (reservation.id in consumedIds) return false
            if (restoredIds.add(reservation.id)) restored += reservation.value
            return true
        }
        override fun deliver(playerId: UUID, value: Long, transactionId: UUID) = deliverResult
    }
}
