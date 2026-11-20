package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.domain.entities.GuildBan
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** REQ-194: guild bans survive a restart and are dropped with the guild. */
class GuildBanRepositorySQLTest : RewardSqlTestFixture() {
    @Test
    fun `bans are saved, listed, removed and kept across a restart`() {
        val storage = openStorage()
        migrateProductionSchema(storage)
        val guild = UUID.randomUUID()
        val other = UUID.randomUUID()
        val player = UUID.randomUUID()
        val second = UUID.randomUUID()
        val by = UUID.randomUUID()
        val at = Instant.parse("2026-11-10T06:00:00Z")
        val repo = GuildBanRepositorySQL(storage)

        assertTrue(repo.add(GuildBan(guild, player, by, at)))
        assertFalse(repo.add(GuildBan(guild, player, by, at)), "a second ban of the same player is not saved")
        assertTrue(repo.add(GuildBan(guild, second, by, at)))
        assertTrue(repo.add(GuildBan(other, player, by, at)))
        assertTrue(repo.isBanned(guild, player))
        assertFalse(repo.isBanned(guild, by))
        closeStorage(storage)

        val reopened = GuildBanRepositorySQL(openStorage())
        assertEquals(setOf(player, second), reopened.bans(guild).map { it.playerId }.toSet())
        assertEquals(GuildBan(guild, player, by, at), reopened.bans(guild).first { it.playerId == player })
        assertTrue(reopened.remove(guild, second))
        assertFalse(reopened.remove(guild, second))
        reopened.removeGuild(guild)
        assertFalse(reopened.isBanned(guild, player))
        assertTrue(reopened.isBanned(other, player), "other guilds keep their bans")
    }
}
