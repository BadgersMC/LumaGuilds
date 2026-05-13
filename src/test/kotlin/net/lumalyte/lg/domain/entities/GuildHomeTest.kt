package net.lumalyte.lg.domain.entities

import net.lumalyte.lg.domain.values.Position3D
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.UUID

class GuildHomeTest {

    @Test
    fun `GuildHome defaults allowedRankIds to empty set`() {
        val home = GuildHome(UUID.randomUUID(), Position3D(0, 64, 0))
        assertEquals(emptySet<UUID>(), home.allowedRankIds)
    }

    @Test
    fun `GuildHome stores supplied allowedRankIds`() {
        val rankA = UUID.randomUUID()
        val rankB = UUID.randomUUID()
        val home = GuildHome(
            worldId = UUID.randomUUID(),
            position = Position3D(0, 64, 0),
            allowedRankIds = setOf(rankA, rankB)
        )
        assertEquals(setOf(rankA, rankB), home.allowedRankIds)
    }
}
