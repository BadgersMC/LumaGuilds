package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.domain.entities.ExperienceAwardRequest
import net.lumalyte.lg.domain.entities.ExperienceAwardResult
import net.lumalyte.lg.domain.values.CapPeriod
import net.lumalyte.lg.domain.values.ExperiencePolicy
import net.lumalyte.lg.domain.values.ExperienceSource
import net.lumalyte.lg.domain.values.ProgressionCurve
import net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadSQLiteStorage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.SQLException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Atomic SQL contract for REQ-049 source caps. */
class ExperienceAwardRepositorySQLTest {

    @TempDir lateinit var tempDir: Path
    private lateinit var storage: VirtualThreadSQLiteStorage
    private lateinit var repository: ExperienceAwardRepositorySQL
    private val guildId = UUID.fromString("00000000-0000-0000-0000-000000000011")
    private val actorId = UUID.fromString("00000000-0000-0000-0000-000000000012")
    private val instant = Instant.parse("2026-08-28T12:00:00Z")
    private val curve = ProgressionCurve(500.0, 1.15, 150, 100)
    private val policy = ExperiencePolicy(
        ExperienceSource.MOB_KILL, "MOB_KILL", 2, 6_000, CapPeriod.DAILY, true,
    )

    @BeforeEach
    fun setUp() {
        storage = VirtualThreadSQLiteStorage(tempDir.toFile())
        storage.connection.executeUpdate("CREATE TABLE guilds (id TEXT PRIMARY KEY, level INTEGER NOT NULL DEFAULT 1)")
        storage.connection.executeUpdate("INSERT INTO guilds (id, level) VALUES (?, 1)", guildId.toString())
        repository = ExperienceAwardRepositorySQL(storage, curve)
    }

    @AfterEach
    fun tearDown() {
        storage.connection.close(5, TimeUnit.SECONDS)
    }

    @Test
    fun `progression cap usage audit and guild level commit together`() {
        val result = repository.awardAtomically(request(), policy, 2, policy.windowContaining(instant))

        assertEquals(ExperienceAwardResult.Awarded(2, 2, true), result)
        assertEquals(2, intValue("SELECT total_experience AS value FROM guild_progression WHERE guild_id = ?", guildId))
        assertEquals(2, intValue("SELECT awarded_xp AS value FROM guild_experience_source_usage WHERE guild_id = ?", guildId))
        assertEquals(2, intValue("SELECT amount AS value FROM experience_transactions WHERE guild_id = ?", guildId))
        assertEquals(1, intValue("SELECT level AS value FROM guilds WHERE id = ?", guildId))
    }

    @Test
    fun `final award is clipped to remaining source allowance`() {
        seedUsage(5_999)

        val result = repository.awardAtomically(request(), policy, 2, policy.windowContaining(instant))

        assertEquals(ExperienceAwardResult.Awarded(1, 6_000, true), result)
        assertEquals(1, intValue("SELECT total_experience AS value FROM guild_progression WHERE guild_id = ?", guildId))
        assertEquals(6_000, intValue("SELECT awarded_xp AS value FROM guild_experience_source_usage WHERE guild_id = ?", guildId))
        assertEquals(1, intValue("SELECT amount AS value FROM experience_transactions WHERE guild_id = ?", guildId))
    }

    @Test
    fun `exhausted source changes no progression or audit row`() {
        seedUsage(6_000)

        val result = repository.awardAtomically(request(), policy, 2, policy.windowContaining(instant))

        assertEquals(ExperienceAwardResult.NoAllowance(6_000, 6_000), result)
        assertEquals(0, rowCount("guild_progression"))
        assertEquals(0, rowCount("experience_transactions"))
    }

    @Test
    fun `unlimited award writes progression and audit without usage row`() {
        val weekly = ExperiencePolicy(
            ExperienceSource.WEEKLY_ACTIVITY, "WEEKLY_ACTIVITY", 1, 0, CapPeriod.UNLIMITED, true,
        )

        val result = repository.awardAtomically(
            request().copy(actorId = null, source = ExperienceSource.WEEKLY_ACTIVITY, units = 25_000),
            weekly,
            25_000,
            null,
        )

        assertEquals(ExperienceAwardResult.Awarded(25_000, 25_000, false, leveledUpTo = 7), result)
        assertEquals(25_000, intValue("SELECT total_experience AS value FROM guild_progression WHERE guild_id = ?", guildId))
        assertEquals(0, rowCount("guild_experience_source_usage"))
    }

    @Test
    fun `audit failure rolls back usage progression and guild level`() {
        storage.connection.executeUpdate(
            "CREATE TRIGGER reject_xp_audit BEFORE INSERT ON experience_transactions BEGIN SELECT RAISE(ABORT, 'audit rejected'); END"
        )

        assertThrows(SQLException::class.java) {
            repository.awardAtomically(request(), policy, 2, policy.windowContaining(instant))
        }

        assertEquals(0, rowCount("guild_progression"))
        assertEquals(0, rowCount("guild_experience_source_usage"))
        assertEquals(0, rowCount("experience_transactions"))
        assertEquals(1, intValue("SELECT level AS value FROM guilds WHERE id = ?", guildId))
    }

    private fun request() = ExperienceAwardRequest(
        guildId, actorId, ExperienceSource.MOB_KILL, 1, instant, eligible = true,
    )

    private fun seedUsage(amount: Int) {
        val window = requireNotNull(policy.windowContaining(instant))
        storage.connection.executeUpdate(
            "INSERT INTO guild_experience_source_usage (guild_id, source_pool, period_start, period_end, awarded_xp) VALUES (?, ?, ?, ?, ?)",
            guildId.toString(), policy.pool, window.startInclusive.toEpochMilli(), window.endExclusive.toEpochMilli(), amount,
        )
    }

    private fun intValue(sql: String, guildId: UUID): Int =
        storage.connection.getFirstRow(sql, guildId.toString())!!.getInt("value")

    private fun rowCount(table: String): Int =
        storage.connection.getFirstRow("SELECT COUNT(*) AS value FROM $table")!!.getInt("value")
}
