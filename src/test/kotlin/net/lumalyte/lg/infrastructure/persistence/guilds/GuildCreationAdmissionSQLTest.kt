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
        assertTrue(repository.addCreated(Guild(UUID.randomUUID(), "Retry", createdAt = Instant.now()), creator))
    }

    private fun repository(storage: net.lumalyte.lg.infrastructure.persistence.storage.Storage<co.aikar.idb.Database>): GuildRepositorySQLite {
        if (storage.dialect == SqlDialect.MARIADB) {
            val plugin = io.mockk.mockk<org.bukkit.plugin.java.JavaPlugin>(relaxed = true)
            io.mockk.every { plugin.getComponentLogger() } returns net.kyori.adventure.text.logger.slf4j.ComponentLogger.logger("CreationMigrationTest")
            storage.connection.connection.use {
                net.lumalyte.lg.infrastructure.persistence.migrations.MariaDBMigrations(plugin, it).migrate()
            }
        }
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
}
