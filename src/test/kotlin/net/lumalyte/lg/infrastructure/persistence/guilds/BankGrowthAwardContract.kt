package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.services.PermanentExperienceService
import net.lumalyte.lg.application.services.PlaytimeActivityService
import net.lumalyte.lg.domain.entities.AwardRejection
import net.lumalyte.lg.domain.entities.ExperienceAwardRequest
import net.lumalyte.lg.domain.entities.ExperienceAwardResult
import net.lumalyte.lg.domain.entities.PreparedExperienceAward
import net.lumalyte.lg.domain.values.CapPeriod
import net.lumalyte.lg.domain.values.ExperiencePolicy
import net.lumalyte.lg.domain.values.ExperienceSource
import net.lumalyte.lg.domain.values.ProgressionCurve
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.SQLException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** The same real transaction contracts execute on isolated SQLite and opt-in loopback MariaDB. */
abstract class BankGrowthAwardContract {
    private var storage: Storage<Database>? = null
    private val db get() = checkNotNull(storage)
    private lateinit var awards: ExperienceAwardRepositorySQL
    private lateinit var bank: BankProgressionRepositorySQL
    private lateinit var permanent: PermanentExperienceService
    private val guild = UUID.randomUUID()
    private val actor = UUID.randomUUID()
    private val at = Instant.parse("2026-10-20T12:00:00Z")
    private val policy = ExperiencePolicy(ExperienceSource.BANK_DEPOSIT, "BANK_DEPOSIT", 1, 100, CapPeriod.DAILY, true)
    private val window = checkNotNull(policy.windowContaining(at))

    protected abstract fun newStorage(): Storage<Database>

    @BeforeEach fun setupBankAward() {
        storage = newStorage()
        listOf("guild_bank_xp_high_water", "experience_transactions", "guild_experience_source_usage",
            "guild_progression", "guilds").forEach { db.connection.executeUpdate("DROP TABLE IF EXISTS $it") }
        val engine = if (db.dialect == SqlDialect.MARIADB) " ENGINE=InnoDB" else ""
        db.connection.executeUpdate("CREATE TABLE guilds (id VARCHAR(36) PRIMARY KEY, level INT NOT NULL)$engine")
        db.connection.executeUpdate("INSERT INTO guilds VALUES (?, 1)", guild.toString())
        awards = ExperienceAwardRepositorySQL(db, ProgressionCurve(500.0, 1.15, 150, 100))
        bank = BankProgressionRepositorySQL(db, awards)
        permanent = PermanentExperienceService(awards, object : PlaytimeActivityService {
            override fun isXpBlocked(playerId: UUID) = false
        })
    }

    @AfterEach fun closeBankAward() {
        storage?.connection?.close(5, TimeUnit.SECONDS)
    }

    @Test fun firstWindowAuditFailureRollsBackAndRetries() {
        failReceipts()
        assertFailsWith<SQLException> { award(0, 100) }
        assertState(null, 0, 0)
        allowReceipts()

        assertEquals(ExperienceAwardResult.Awarded(1, 1, true), award(0, 100))
        assertNull(award(0, 100))
        assertState(100, 1, 1)
    }

    @Test fun existingHighWaterSurvivesFailedLaterAward() {
        award(0, 100)
        failReceipts()
        assertFailsWith<SQLException> { award(100, 200) }
        assertState(100, 1, 1)
        allowReceipts()

        assertEquals(ExperienceAwardResult.Awarded(1, 2, true), award(100, 200))
        assertState(200, 2, 2)
    }

    @Test fun concurrentSameSnapshotAwardsOnceAcrossRepositoryInstances() {
        val second = BankProgressionRepositorySQL(db, awards)
        val start = CountDownLatch(1)
        val attempts = listOf(bank, second).map { repository ->
            CompletableFuture.supplyAsync {
                start.await()
                repository.awardNetNewUnits(guild, 0, 200, 100, window, preparation())
            }
        }
        start.countDown()
        val results = attempts.map { it.get(10, TimeUnit.SECONDS) }

        assertEquals(1, results.filterIsInstance<ExperienceAwardResult.Awarded>().size)
        assertEquals(1, results.count { it == null })
        assertState(200, 2, 1)
    }

    @Test fun withdrawalAndRedepositDoNotRepeatBankXp() {
        award(0, 200)

        assertNull(award(200, 100))
        assertNull(award(100, 200))
        assertState(200, 2, 1)
    }

    @Test fun capClippingAndExhaustionStillConsumeHighWater() {
        val capped = policy.copy(capXp = 2)
        assertEquals(ExperienceAwardResult.Awarded(2, 2, true), award(0, 300, capped))
        assertEquals(ExperienceAwardResult.NoAllowance(2, 2), award(300, 400, capped))

        assertNull(award(0, 400, policy))
        assertState(400, 2, 1)
    }

    @Test fun disabledSourceStillConsumesHighWater() {
        assertEquals(
            ExperienceAwardResult.Rejected(AwardRejection.SOURCE_DISABLED),
            award(0, 100, policy.copy(enabled = false, awardXp = 0)),
        )

        assertNull(award(0, 100))
        assertState(100, 0, 0)
    }

    @Test fun noUnitsNeverEvaluatesPreparedDecision() {
        assertNull(bank.awardNetNewUnits(guild, 100, 100, 100, window) { error("No award should be prepared") })
        assertState(100, 0, 0)
    }

    @Test fun missingGuildCannotCreateAwardState() {
        db.connection.executeUpdate("DELETE FROM guilds WHERE id = ?", guild.toString())

        assertFailsWith<IllegalStateException> { award(0, 100) }

        assertState(null, 0, 0)
    }

    @Test fun mismatchedPreparedAwardRollsBackReservation() {
        val ready = preparation()(1) as PreparedExperienceAward.Ready
        val invalid = listOf(
            ready.copy(request = ready.request.copy(guildId = UUID.randomUUID())),
            ready.copy(request = ready.request.copy(units = 2)),
            ready.copy(request = ready.request.copy(source = ExperienceSource.MOB_KILL)),
            ready.copy(policy = ready.policy.copy(source = ExperienceSource.MOB_KILL)),
            ready.copy(request = ready.request.copy(occurredAt = at.plusSeconds(86_400))),
            ready.copy(requestedXp = 0),
            ready.copy(request = ready.request.copy(eligible = false)),
            ready.copy(policy = ready.policy.copy(enabled = false)),
        )
        invalid.forEach { prepared ->
            assertFailsWith<IllegalArgumentException> {
                bank.awardNetNewUnits(guild, 0, 100, 100, window) { prepared }
            }
            assertState(null, 0, 0)
        }
    }

    private fun award(opening: Long, current: Long, selectedPolicy: ExperiencePolicy = policy): ExperienceAwardResult? =
        bank.awardNetNewUnits(guild, opening, current, 100, window, preparation(selectedPolicy))

    private fun preparation(selectedPolicy: ExperiencePolicy = policy): (Int) -> PreparedExperienceAward =
        permanent.prepareForUnits(ExperienceAwardRequest(guild, actor, ExperienceSource.BANK_DEPOSIT, 1, at), selectedPolicy)

    private fun failReceipts() {
        val failure = if (db.dialect == SqlDialect.MARIADB) {
            "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'bank XP receipt rejected'"
        } else {
            "BEGIN SELECT RAISE(ABORT, 'bank XP receipt rejected'); END"
        }
        db.connection.executeUpdate("CREATE TRIGGER reject_bank_award BEFORE INSERT ON experience_transactions $failure")
    }

    private fun allowReceipts() {
        db.connection.executeUpdate("DROP TRIGGER reject_bank_award")
    }

    private fun assertState(highWater: Long?, xp: Int, receipts: Int) {
        assertEquals(highWater, db.connection.getFirstRow(
            "SELECT high_water_balance FROM guild_bank_xp_high_water WHERE guild_id = ?", guild.toString(),
        )?.let { row ->
            when (val value = row.get<Any>("high_water_balance")) {
                is Int -> value.toLong()
                is Long -> value
                else -> error("Expected an integral bank high-water value, got $value")
            }
        })
        assertEquals(xp, sum("guild_progression", "total_experience"))
        assertEquals(xp, sum("guild_experience_source_usage", "awarded_xp"))
        assertEquals(receipts, checkNotNull(db.connection.getFirstRow(
            "SELECT COUNT(*) AS total FROM experience_transactions WHERE guild_id = ?", guild.toString(),
        )).getInt("total"))
    }

    private fun sum(table: String, column: String): Int = checkNotNull(db.connection.getFirstRow(
        "SELECT COALESCE(SUM($column), 0) AS total FROM $table WHERE guild_id = ?", guild.toString(),
    )).getInt("total")
}
