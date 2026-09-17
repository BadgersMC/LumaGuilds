package net.lumalyte.lg.infrastructure.persistence.guilds

import io.mockk.mockk
import net.lumalyte.lg.application.services.PermanentExperienceService
import net.lumalyte.lg.domain.entities.*
import net.lumalyte.lg.domain.values.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.*

class ExperienceBoostRepositorySQLTest : RewardSqlTestFixture() {
    @Test fun `boost accepts only remaining cap and receipt prevents replay after restart and config change`() {
        val storage = openStorage()
        val guild = UUID.randomUUID()
        storage.connection.executeUpdate("CREATE TABLE guilds (id VARCHAR(36) PRIMARY KEY, level INTEGER NOT NULL DEFAULT 1)")
        storage.connection.executeUpdate("INSERT INTO guilds VALUES (?, 1)", guild.toString())
        val curve = ProgressionCurve(500.0, 1.15, 150, 100)
        val repository = ExperienceAwardRepositorySQL(storage, curve)
        val now = Instant.parse("2026-09-18T12:00:00Z")
        val policy = ExperiencePolicy(ExperienceSource.MOB_KILL, "MOB_KILL", 100, 150, CapPeriod.DAILY, true)
        val first = ExperienceAwardRequest(guild, null, policy.source, 1, now)
        var boost: ExperienceBoost? = null
        val service = PermanentExperienceService(repository, mockk()) { boost }
        assertEquals(ExperienceAwardResult.Awarded(100, 100, true), service.award(first, policy))
        boost = ExperienceBoost(now.minusSeconds(1), now.plusSeconds(1), 2.0, setOf(policy.source))
        val second = first.copy(transactionId = UUID.randomUUID())
        assertEquals(ExperienceAwardResult.Awarded(50, 150, true), service.award(second, policy))
        assertEquals(ExperienceAwardResult.NoAllowance(150, 150), service.award(first.copy(transactionId = UUID.randomUUID()), policy))
        assertEquals(150, storage.connection.getFirstRow("SELECT total_experience FROM guild_progression WHERE guild_id = ?", guild.toString())!!.getInt("total_experience"))
        closeStorage(storage)
        val reopened = openStorage()
        val restored = PermanentExperienceService(ExperienceAwardRepositorySQL(reopened, curve), mockk())
        assertEquals(ExperienceAwardResult.Duplicate, restored.award(second, policy))
        assertEquals(150, reopened.connection.getFirstRow("SELECT awarded_xp FROM guild_experience_source_usage WHERE guild_id = ?", guild.toString())!!.getInt("awarded_xp"))
    }

    @Test fun `applicable weekly quest boosts stay outside capped usage`() {
        val storage = openStorage()
        val guild = UUID.randomUUID()
        storage.connection.executeUpdate("CREATE TABLE guilds (id VARCHAR(36) PRIMARY KEY, level INTEGER NOT NULL DEFAULT 1)")
        storage.connection.executeUpdate("INSERT INTO guilds VALUES (?, 1)", guild.toString())
        val now = Instant.parse("2026-09-18T12:00:00Z")
        val source = ExperienceSource.WEEKLY_ACTIVITY
        val service = PermanentExperienceService(ExperienceAwardRepositorySQL(storage, ProgressionCurve(500.0, 1.15, 150, 100)), mockk()) {
            ExperienceBoost(now.minusSeconds(1), now.plusSeconds(1), 2.0, setOf(source))
        }
        assertEquals(ExperienceAwardResult.Awarded(400, 400, false), service.award(
            ExperienceAwardRequest(guild, null, source, 200, now), ExperiencePolicy(source, source.defaultPool, 1, 0, CapPeriod.UNLIMITED, true)))
        assertEquals(0, storage.connection.getFirstRow("SELECT COUNT(*) AS n FROM guild_experience_source_usage")!!.getInt("n"))
    }
}
