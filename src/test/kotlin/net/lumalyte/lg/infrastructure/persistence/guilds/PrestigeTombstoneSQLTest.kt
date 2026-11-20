package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.values.GuildCreationCooldown
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** REQ-163: a prestiged guild's name survives its disband. */
class PrestigeTombstoneSQLTest : RewardSqlTestFixture() {
    @Test
    fun `disbanding a prestiged guild records its name and tier, others record nothing`() {
        val storage = openStorage()
        migrateProductionSchema(storage)
        val guilds = GuildRepositorySQLite(storage)
        val prestiged = Guild(UUID.randomUUID(), "Everlights", createdAt = Instant.now())
        val plain = Guild(UUID.randomUUID(), "Fresh", createdAt = Instant.now())
        assertTrue(guilds.addCreated(prestiged, UUID.randomUUID()))
        assertTrue(guilds.addCreated(plain, UUID.randomUUID()))
        // Creating a guild opens its reward account at prestige 0; give one of them two prestiges.
        storage.connection.executeUpdate(
            "UPDATE guild_reward_accounts SET prestige_count = 2 WHERE guild_id = ?",
            prestiged.id.toString(),
        )
        val at = Instant.now()

        assertTrue(guilds.removeWithCreationCooldown(prestiged.id, GuildCreationCooldown(), at))
        assertTrue(guilds.removeWithCreationCooldown(plain.id, GuildCreationCooldown(), at))

        val tombstones = PrestigeTombstonesSQL(storage)
        val record = tombstones.disbanded(prestiged.id)!!
        assertEquals("Everlights", record.name)
        assertEquals(2, record.prestigeCount)
        assertEquals(at.toEpochMilli(), record.disbandedAt.toEpochMilli())
        assertNull(tombstones.disbanded(plain.id))

        // A guild re-founded under the same name is a new guild with no history.
        val refounded = Guild(UUID.randomUUID(), "Everlights", createdAt = Instant.now())
        assertTrue(guilds.addCreated(refounded, UUID.randomUUID()))
        assertNull(tombstones.disbanded(refounded.id))
    }
}
