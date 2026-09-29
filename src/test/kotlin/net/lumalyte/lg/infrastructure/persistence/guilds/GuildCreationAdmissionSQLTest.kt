package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.values.GuildCreationCooldown
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.*

class GuildCreationAdmissionSQLTest : RewardSqlTestFixture() {
    @Test fun `failed setup cleanup never starts a creation cooldown`() {
        val storage = openStorage()
        val repository = repository(storage)
        val creator = UUID.randomUUID()
        val guild = Guild(UUID.randomUUID(), "Setup Failure", createdAt = Instant.now())
        assertTrue(repository.addCreated(guild, creator))
        assertTrue(repository.remove(guild.id))
        assertNull(repository.creationCooldownUntil(creator))
        assertEquals(
            0,
            storage.connection.getFirstRow(
                "SELECT COUNT(*) AS n FROM guild_reward_accounts WHERE guild_id = ?",
                guild.id.toString(),
            )!!.getInt("n"),
        )
        assertTrue(repository.addCreated(Guild(UUID.randomUUID(), "Retry", createdAt = Instant.now()), creator))
    }

    private fun repository(storage: net.lumalyte.lg.infrastructure.persistence.storage.Storage<co.aikar.idb.Database>): GuildRepositorySQLite {
        migrateProductionSchema(storage)
        return GuildRepositorySQLite(storage)
    }
    @Test fun `real guild admission and deletion persist cooldown atomically`() {
        val storage = openStorage()
        val repository = repository(storage)
        val creator = UUID.randomUUID()
        val at = Instant.parse("2026-09-17T00:00:00Z")
        val guild = Guild(UUID.randomUUID(), "First", createdAt = at)
        assertTrue(repository.addCreated(guild, creator))
        assertEquals(guild, repository.getById(guild.id))
        assertTrue(repository.removeWithCreationCooldown(guild.id, GuildCreationCooldown(), at.plusSeconds(1)))
        assertNull(repository.getById(guild.id))
        val expires = repository.creationCooldownUntil(creator)!!
        assertFalse(repository.addCreated(Guild(UUID.randomUUID(), "Blocked", createdAt = at.plusSeconds(2)), creator))
        assertTrue(repository.addCreated(Guild(UUID.randomUUID(), "Allowed", createdAt = expires), creator))
    }

    @Test fun `history write failure rolls back guild deletion and keeps the cache`() {
        val storage = openStorage()
        val repository = repository(storage)
        val creator = UUID.randomUUID()
        val at = Instant.parse("2026-09-17T00:00:00Z")
        val guild = Guild(UUID.randomUUID(), "Keep", createdAt = at)
        assertTrue(repository.addCreated(guild, creator))
        val body = if (storage.dialect == SqlDialect.MARIADB)
            "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'injected'"
        else "BEGIN SELECT RAISE(ABORT, 'injected'); END"
        storage.connection.executeUpdate("CREATE TRIGGER reject_creator_update BEFORE UPDATE ON guild_creators $body")
        assertFalse(repository.removeWithCreationCooldown(guild.id, GuildCreationCooldown(), at.plusSeconds(1)))
        assertEquals(guild, repository.getById(guild.id))
        assertNull(repository.creationCooldownUntil(creator))
        assertEquals(1, storage.connection.getFirstRow("SELECT COUNT(*) AS n FROM guilds WHERE id = ?", guild.id.toString())!!.getInt("n"))
    }

    @Test fun `origin insert failure cannot leave a guild or publish it in cache`() {
        val storage = openStorage()
        val repository = repository(storage)
        GuildCreationHistorySQL(storage)
        val body = if (storage.dialect == SqlDialect.MARIADB)
            "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'injected'"
        else "BEGIN SELECT RAISE(ABORT, 'injected'); END"
        storage.connection.executeUpdate("CREATE TRIGGER reject_creator_insert BEFORE INSERT ON guild_creators $body")
        val guild = Guild(UUID.randomUUID(), "Rollback", createdAt = Instant.now())
        assertFalse(repository.addCreated(guild, UUID.randomUUID()))
        assertNull(repository.getById(guild.id))
        assertEquals(0, storage.connection.getFirstRow("SELECT COUNT(*) AS n FROM guilds")!!.getInt("n"))
    }

    @Test fun `new guild creation atomically initializes prestige reward state`() {
        val storage = openStorage()
        val repository = repository(storage)
        val guild = Guild(UUID.randomUUID(), "Prestige Ready", createdAt = Instant.now())

        assertTrue(repository.addCreated(guild, UUID.randomUUID()))

        val row = storage.connection.getFirstRow(
            "SELECT version, initial_home_capacity, prestige_count FROM guild_reward_accounts WHERE guild_id = ?",
            guild.id.toString(),
        )
        assertNotNull(row)
        assertEquals(0, row.getInt("version"))
        assertEquals(1, row.getInt("initial_home_capacity"))
        assertEquals(0, row.getInt("prestige_count"))
    }

    @Test fun `reward account initialization failure rolls back guild creation atomically`() {
        val storage = openStorage()
        val repository = repository(storage)
        rejectInserts(storage, "guild_reward_accounts")
        val guild = Guild(UUID.randomUUID(), "No Partial Guild", createdAt = Instant.now())

        assertFalse(repository.addCreated(guild, UUID.randomUUID()))

        assertNull(repository.getById(guild.id))
        assertEquals(
            0,
            storage.connection.getFirstRow(
                "SELECT COUNT(*) AS n FROM guilds WHERE id = ?",
                guild.id.toString(),
            )!!.getInt("n"),
        )
        assertEquals(
            0,
            storage.connection.getFirstRow(
                "SELECT COUNT(*) AS n FROM guild_creators WHERE guild_id = ?",
                guild.id.toString(),
            )!!.getInt("n"),
        )
    }
}
