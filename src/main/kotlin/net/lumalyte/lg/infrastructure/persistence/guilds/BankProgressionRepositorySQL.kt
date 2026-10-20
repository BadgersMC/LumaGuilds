package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.persistence.BankProgressionRepository
import net.lumalyte.lg.application.services.ChapterTwoGuildAwardRules
import net.lumalyte.lg.domain.entities.ExperienceAwardResult
import net.lumalyte.lg.domain.entities.PreparedExperienceAward
import net.lumalyte.lg.domain.values.ExperienceSource
import net.lumalyte.lg.domain.values.PeriodWindow
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import java.sql.Connection
import java.util.UUID

class BankProgressionRepositorySQL(
    private val storage: Storage<Database>,
    private val awards: ExperienceAwardRepositorySQL,
) : BankProgressionRepository {
    private val mariaDb = storage.dialect == SqlDialect.MARIADB

    init {
        storage.connection.executeUpdate(
            """
            CREATE TABLE IF NOT EXISTS guild_bank_xp_high_water (
                guild_id VARCHAR(36) NOT NULL,
                period_start BIGINT NOT NULL,
                period_end BIGINT NOT NULL,
                high_water_balance BIGINT NOT NULL DEFAULT 0,
                PRIMARY KEY (guild_id, period_start)
            )
            """.trimIndent()
        )
    }

    override fun awardNetNewUnits(
        guildId: UUID,
        openingBalance: Long,
        currentBalance: Long,
        valuePerUnit: Long,
        window: PeriodWindow,
        prepareAward: (Int) -> PreparedExperienceAward,
    ): ExperienceAwardResult? = storage.connection.connection.use { connection ->
        connection.committingTransaction {
            lockGuild(connection, guildId)
            val units = reserve(connection, guildId, openingBalance, currentBalance, valuePerUnit, window)
            if (units <= 0) null
            else awardPrepared(connection, guildId, units, window, prepareAward(units))
        }
    }

    override fun reserveNetNewUnits(
        guildId: UUID,
        openingBalance: Long,
        currentBalance: Long,
        valuePerUnit: Long,
        window: PeriodWindow,
    ): Int = storage.connection.connection.use { connection ->
        connection.committingTransaction {
            reserve(connection, guildId, openingBalance, currentBalance, valuePerUnit, window)
        }
    }

    private fun lockGuild(connection: Connection, guildId: UUID) {
        val present = if (mariaDb) {
            connection.selectOne("SELECT 1 FROM guilds WHERE id = ? FOR UPDATE", guildId.toString()) { true } ?: false
        } else {
            // Take the SQLite writer before any SELECT, and reject an already deleted guild.
            connection.updateStatement("UPDATE guilds SET level = level WHERE id = ?", guildId.toString()) == 1
        }
        check(present) { "Guild $guildId does not exist" }
    }

    private fun reserve(
        connection: Connection,
        guildId: UUID,
        openingBalance: Long,
        currentBalance: Long,
        valuePerUnit: Long,
        window: PeriodWindow,
    ): Int {
        connection.updateStatement(
            seedSql(), guildId.toString(), window.startInclusive.toEpochMilli(),
            window.endExclusive.toEpochMilli(), openingBalance,
        )
        val locking = if (mariaDb) " FOR UPDATE" else ""
        val previous = checkNotNull(connection.selectOne(
            "SELECT high_water_balance FROM guild_bank_xp_high_water WHERE guild_id = ? AND period_start = ?$locking",
            guildId.toString(), window.startInclusive.toEpochMilli(),
        ) { it.getLong("high_water_balance") }) { "Bank high-water row was not created" }
        val units = ChapterTwoGuildAwardRules.netNewBankUnits(previous, currentBalance, valuePerUnit)
        if (currentBalance > previous) {
            connection.updateStatement(
                "UPDATE guild_bank_xp_high_water SET period_end = ?, high_water_balance = ? WHERE guild_id = ? AND period_start = ?",
                window.endExclusive.toEpochMilli(), currentBalance, guildId.toString(), window.startInclusive.toEpochMilli(),
            )
        }
        return units
    }

    private fun awardPrepared(
        connection: Connection,
        guildId: UUID,
        units: Int,
        window: PeriodWindow,
        prepared: PreparedExperienceAward,
    ): ExperienceAwardResult = when (prepared) {
        is PreparedExperienceAward.Rejected -> ExperienceAwardResult.Rejected(prepared.reason)
        is PreparedExperienceAward.Ready -> {
            require(prepared.request.guildId == guildId) { "Prepared award must match the locked guild" }
            require(prepared.request.units == units) { "Prepared award must match reserved bank units" }
            require(prepared.request.source == ExperienceSource.BANK_DEPOSIT &&
                prepared.policy.source == ExperienceSource.BANK_DEPOSIT) { "Prepared award must use the bank source" }
            require(prepared.policy.windowContaining(prepared.request.occurredAt) == window) {
                "Prepared award must match the bank period"
            }
            require(prepared.requestedXp > 0 && prepared.request.eligible && prepared.policy.enabled) {
                "Prepared award must be eligible with positive XP"
            }
            awards.awardInTransaction(connection, prepared.request, prepared.policy, prepared.requestedXp)
        }
    }

    private fun seedSql(): String = if (mariaDb) {
        """
        INSERT INTO guild_bank_xp_high_water
            (guild_id, period_start, period_end, high_water_balance)
        VALUES (?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE period_end = VALUES(period_end)
        """.trimIndent()
    } else {
        """
        INSERT INTO guild_bank_xp_high_water
            (guild_id, period_start, period_end, high_water_balance)
        VALUES (?, ?, ?, ?)
        ON CONFLICT(guild_id, period_start) DO UPDATE SET period_end = excluded.period_end
        """.trimIndent()
    }
}
