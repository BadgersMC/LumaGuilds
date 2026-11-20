package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.domain.entities.GuildHome
import net.lumalyte.lg.domain.values.Position3D
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** REQ-180, REQ-182: allied-home slots and access rules survive a restart. */
class AllyHomeAccessSQLTest : RewardSqlTestFixture() {
    private val host = UUID.randomUUID()
    private val ally = UUID.randomUUID()
    private val rank = UUID.randomUUID()
    private val player = UUID.randomUUID()

    @Test
    fun `rules, blocks and the second home persist and reload`() {
        val storage = openStorage()
        migrateProductionSchema(storage)
        val repo = AllyHomeAccessRepositorySQL(storage)
        val home = GuildHome(UUID.randomUUID(), Position3D(10, 70, -4))

        assertTrue(repo.setVisitorRanks(host, ally, setOf(rank)))
        assertTrue(repo.setBlocked(host, player, true))
        assertTrue(repo.setSecondHome(host, home))
        assertTrue(repo.setOutboundRanks(host, ally, setOf(rank)))

        val reloaded = AllyHomeAccessRepositorySQL(storage)
        assertEquals(mapOf(ally to setOf(rank)), reloaded.access(host).visitorRanks)
        assertEquals(setOf(player), reloaded.access(host).blockedPlayers)
        assertEquals(home, reloaded.secondHome(host))
        assertEquals(mapOf(ally to setOf(rank)), reloaded.outboundRanks(host))

        assertTrue(reloaded.setVisitorRanks(host, ally, null))
        assertTrue(reloaded.setBlocked(host, player, false))
        assertTrue(reloaded.setSecondHome(host, null))
        assertTrue(reloaded.setOutboundRanks(host, ally, null))
        val cleared = AllyHomeAccessRepositorySQL(storage)
        assertEquals(emptyMap(), cleared.access(host).visitorRanks)
        assertEquals(emptySet(), cleared.access(host).blockedPlayers)
        assertNull(cleared.secondHome(host))
        assertEquals(emptyMap(), cleared.outboundRanks(host))
    }

    @Test
    fun `deleting a guild removes its rules and home`() {
        val storage = openStorage()
        migrateProductionSchema(storage)
        val repo = AllyHomeAccessRepositorySQL(storage)
        repo.setVisitorRanks(host, ally, emptySet())
        repo.setBlocked(host, player, true)
        repo.setSecondHome(host, GuildHome(UUID.randomUUID(), Position3D(0, 64, 0)))
        repo.setOutboundRanks(host, ally, setOf(rank))

        repo.deleteGuild(host)

        val reloaded = AllyHomeAccessRepositorySQL(storage)
        assertEquals(emptyMap(), reloaded.access(host).visitorRanks)
        assertEquals(emptySet(), reloaded.access(host).blockedPlayers)
        assertNull(reloaded.secondHome(host))
        assertEquals(emptyMap(), reloaded.outboundRanks(host))
    }
}
