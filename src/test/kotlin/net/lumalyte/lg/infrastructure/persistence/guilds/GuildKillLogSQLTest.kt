package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.application.persistence.GuildRival
import net.lumalyte.lg.domain.entities.Kill
import net.lumalyte.lg.domain.values.CombatSummary
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** REQ-199 to REQ-202: the kill log on SQLite and MariaDB. */
class GuildKillLogSQLTest : RewardSqlTestFixture() {
    private val a = UUID.randomUUID()
    private val b = UUID.randomUUID()
    private val c = UUID.randomUUID()
    private val alice = UUID.randomUUID()
    private val ann = UUID.randomUUID()
    private val bob = UUID.randomUUID()
    private val cat = UUID.randomUUID()
    private val t0 = Instant.parse("2026-11-01T00:00:00Z")

    private fun kill(killer: UUID, kg: UUID?, victim: UUID, vg: UUID?, minutes: Long, weapon: String? = "IRON_SWORD") =
        Kill(UUID.randomUUID(), killer, victim, kg, vg, t0.plusSeconds(minutes * 60), weapon)

    @Test
    fun `summaries, recent, between, counts and rivals`() {
        val storage = openStorage()
        migrateProductionSchema(storage)
        val log = GuildKillLogSQL(storage)
        val k1 = kill(alice, a, bob, b, 0)
        val k2 = kill(ann, a, bob, b, 20)
        val k3 = kill(bob, b, alice, a, 40, weapon = null)
        val k4 = kill(alice, a, cat, c, 60)
        val k5 = kill(cat, null, ann, a, 80)
        listOf(k1, k2, k3, k4, k5).forEach(log::record)
        closeStorage(storage)

        val reopened = GuildKillLogSQL(openStorage())
        assertEquals(CombatSummary(kills = 3, deaths = 2), reopened.guildSummary(a))
        assertEquals(CombatSummary(kills = 2, deaths = 1), reopened.guildSummary(a, from = t0.plusSeconds(10 * 60), to = t0.plusSeconds(70 * 60)))
        assertEquals(CombatSummary(kills = 2, deaths = 1), reopened.playerSummary(alice))
        assertEquals(listOf(k5, k4, k3, k2), reopened.recent(a, 4))
        assertEquals(listOf(k3, k2, k1), reopened.between(b, a, 10))
        assertEquals(mapOf(alice to 2, ann to 1), reopened.killCounts(listOf(alice, ann, UUID.randomUUID())))
        assertEquals(emptyMap(), reopened.killCounts(emptyList()))
        assertEquals(listOf(GuildRival(b, kills = 2, deaths = 1), GuildRival(c, kills = 1, deaths = 0)), reopened.rivals(a, 5))
        assertEquals(t0.plusSeconds(60 * 60), reopened.lastKill(alice, cat))
        assertNull(reopened.lastKill(cat, alice))
    }
}
