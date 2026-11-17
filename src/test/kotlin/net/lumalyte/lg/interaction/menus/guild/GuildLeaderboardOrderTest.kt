package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.domain.entities.Guild
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class GuildLeaderboardOrderTest {
    private fun guild(name: String) = Guild(UUID.randomUUID(), name, createdAt = Instant.EPOCH)

    @Test
    fun `REQ-284 a prestige outranks any level on the level standings`() {
        val fresh = guild("Fresh"); val veteran = guild("Veteran"); val climber = guild("Climber")
        val scores = mapOf(fresh.id to 12, veteran.id to 96, climber.id to 100)
        val tiers = mapOf(fresh.id to 6)
        val order = listOf(veteran, climber, fresh).sortedWith(GuildLeaderboardSource.order(0, scores, tiers))
        assertEquals(listOf("Fresh", "Climber", "Veteran"), order.map { it.name })
    }

    @Test
    fun `other tabs ignore prestige`() {
        val a = guild("A"); val b = guild("B")
        val order = listOf(a, b).sortedWith(GuildLeaderboardSource.order(1, mapOf(a.id to 1, b.id to 5), mapOf(a.id to 6)))
        assertEquals(listOf("B", "A"), order.map { it.name })
    }
}
