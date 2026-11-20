package net.lumalyte.lg.infrastructure.persistence.guilds

import java.time.Instant
import java.util.UUID
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import org.junit.jupiter.api.Test
import kotlin.test.*

class GuildDescriptionPersistenceTest : RewardSqlTestFixture() {
    @Test fun `description persists on insert update reload and explicit clear`() {
        val storage = openStorage()
        migrateProductionSchema(storage)
        val guild = Guild(UUID.randomUUID(), "DescriptionTest", createdAt = Instant.now(), description = "Welcome <gold>friends</gold>!")
        val initial = GuildRepositorySQLite(storage)
        assertTrue(initial.add(guild))
        val reloaded = GuildRepositorySQLite(storage)
        assertEquals(guild.description, reloaded.getById(guild.id)?.description)
        assertTrue(reloaded.update(reloaded.getById(guild.id)!!.copy(description = "New description")))
        val afterUpdate = GuildRepositorySQLite(storage)
        assertEquals("New description", afterUpdate.getById(guild.id)?.description)
        assertTrue(afterUpdate.update(afterUpdate.getById(guild.id)!!.copy(description = null)))
        assertNull(GuildRepositorySQLite(storage).getById(guild.id)?.description)
    }

    @Test fun `existing guild is retained when upgrading schema without description column`() {
        val storage = openStorage()
        migrateProductionSchema(storage)
        val guild = Guild(UUID.randomUUID(), "LegacyGuild", createdAt = Instant.now(), level = 4)
        assertTrue(GuildRepositorySQLite(storage).add(guild))
        storage.connection.executeUpdate("ALTER TABLE guilds DROP COLUMN description")
        migrateProductionSchema(storage)
        migrateProductionSchema(storage)
        val upgraded = GuildRepositorySQLite(storage)
        val loaded = upgraded.getById(guild.id)!!
        assertEquals(guild.name, loaded.name)
        assertEquals(4, loaded.level)
        assertNull(loaded.description)
        assertTrue(upgraded.update(loaded.copy(description = "Now retained")))
        assertEquals("Now retained", GuildRepositorySQLite(storage).getById(guild.id)?.description)
    }

    @Test fun `failed description update leaves saved and cached values unchanged`() {
        val storage = openStorage()
        migrateProductionSchema(storage)
        val guild = Guild(UUID.randomUUID(), "WriteFailure", createdAt = Instant.now(), description = "Original")
        val repository = GuildRepositorySQLite(storage)
        assertTrue(repository.add(guild))
        val body = if (storage.dialect == SqlDialect.MARIADB)
            "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'injected failure'"
        else "BEGIN SELECT RAISE(ABORT, 'injected failure'); END"
        storage.connection.executeUpdate("CREATE TRIGGER reject_description_update BEFORE UPDATE ON guilds $body")
        assertFalse(repository.update(guild.copy(description = "Not saved")))
        assertEquals("Original", repository.getById(guild.id)?.description)
        assertEquals("Original", GuildRepositorySQLite(storage).getById(guild.id)?.description)
    }

    @Test fun `legacy updates that do not mention description preserve it`() {
        val storage = openStorage()
        migrateProductionSchema(storage)
        val guild = Guild(UUID.randomUUID(), "LegacyUpdate", createdAt = Instant.now(), description = "Preserved")
        assertTrue(GuildRepositorySQLite(storage).add(guild))
        storage.connection.executeUpdate("UPDATE guilds SET name = ? WHERE id = ?", "Renamed", guild.id.toString())
        val reloaded = GuildRepositorySQLite(storage).getById(guild.id)!!
        assertEquals("Renamed", reloaded.name)
        assertEquals("Preserved", reloaded.description)
    }

    @Test fun `description remains after closing and reopening database storage`() {
        val storage = openStorage()
        migrateProductionSchema(storage)
        val guild = Guild(UUID.randomUUID(), "RestartGuild", createdAt = Instant.now(), description = "Survives restart")
        assertTrue(GuildRepositorySQLite(storage).add(guild))
        closeStorage(storage)
        val reopened = openStorage()
        migrateProductionSchema(reopened)
        assertEquals("Survives restart", GuildRepositorySQLite(reopened).getById(guild.id)?.description)
    }
}
