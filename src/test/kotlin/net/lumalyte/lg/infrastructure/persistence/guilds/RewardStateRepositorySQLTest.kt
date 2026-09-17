package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.domain.rewards.*
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.*

class RewardStateRepositorySQLTest : RewardSqlTestFixture() {
    @Test fun `read is fresh after ownership changes and survives reopen`() {
        val guild = UUID.randomUUID()
        val storage = openStorage()
        val catalog = RewardCatalog.chapterTwo()
        val owners = RewardOwnershipRepositorySQL(storage, catalog)
        storage.connection.executeUpdate("CREATE TABLE guild_progression (guild_id VARCHAR(36) PRIMARY KEY, current_level INTEGER NOT NULL)")
        storage.connection.executeUpdate("INSERT INTO guild_progression VALUES (?, 100)", guild.toString())
        owners.initialize(guild, 8)
        val reader = RewardStateRepositorySQL(storage, owners)
        val before = assertIs<RewardStateRead.Found>(reader.read(guild))
        assertEquals(100, before.level)
        assertEquals(0L, before.snapshot.version)
        assertIs<RewardOwnershipWrite.Saved>(owners.save(guild, 0,
            RewardOwnership(currentRun = setOf("bank-1"), permanent = setOf("home-1"), initialHomeCapacity = 8)))
        val after = assertIs<RewardStateRead.Found>(reader.read(guild))
        assertEquals(1L, after.snapshot.version)
        assertEquals(setOf("home-1"), after.snapshot.ownership.permanent)
        assertTrue(before.snapshot.ownership.permanent.isEmpty())
        closeStorage(storage)
        val reopened = openStorage()
        val restored = assertIs<RewardStateRead.Found>(RewardStateRepositorySQL(reopened,
            RewardOwnershipRepositorySQL(reopened, catalog)).read(guild))
        assertEquals(1L, restored.snapshot.version)
        assertEquals(8, restored.snapshot.ownership.initialHomeCapacity)
    }

    @Test fun `missing progression and ownership never create default accounts`() {
        val guild = UUID.randomUUID()
        val storage = openStorage()
        val owners = RewardOwnershipRepositorySQL(storage, RewardCatalog.chapterTwo())
        storage.connection.executeUpdate("CREATE TABLE guild_progression (guild_id VARCHAR(36) PRIMARY KEY, current_level INTEGER NOT NULL)")
        val reader = RewardStateRepositorySQL(storage, owners)
        assertEquals(RewardStateRead.Missing, reader.read(guild))
        storage.connection.executeUpdate("INSERT INTO guild_progression VALUES (?, 1)", guild.toString())
        assertEquals(RewardStateRead.Missing, reader.read(guild))
        assertEquals(RewardOwnershipRead.Missing, owners.read(guild))
        owners.initialize(guild, 1)
        storage.connection.executeUpdate("DELETE FROM guild_progression")
        assertEquals(RewardStateRead.Missing, reader.read(guild))
    }

    @Test fun `corrupt ownership and SQL failures remain failed`() {
        val guild = UUID.randomUUID()
        val storage = openStorage()
        val owners = RewardOwnershipRepositorySQL(storage, RewardCatalog.chapterTwo())
        val reader = RewardStateRepositorySQL(storage, owners)
        assertEquals(RewardStateRead.Failed, reader.read(guild)) // progression table absent
        storage.connection.executeUpdate("CREATE TABLE guild_progression (guild_id VARCHAR(36) PRIMARY KEY, current_level INTEGER NOT NULL)")
        storage.connection.executeUpdate("INSERT INTO guild_progression VALUES (?, 1)", guild.toString())
        owners.initialize(guild, 1)
        storage.connection.executeUpdate("INSERT INTO guild_reward_ownership VALUES (?, 'unknown-reward', 1)", guild.toString())
        assertEquals(RewardStateRead.Failed, reader.read(guild))
    }
}
