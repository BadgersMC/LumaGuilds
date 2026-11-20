package net.lumalyte.lg.application.services

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.api.GuildPrestigePerkLookupImpl
import net.lumalyte.lg.config.PrestigePerksConfig
import net.lumalyte.lg.domain.rewards.PrestigePerks
import net.lumalyte.lg.domain.rewards.PrestigeStanding
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

/** REQ-181: the stall bonus follows the guild's cached prestige standing. */
class GuildPrestigePerksTest {
    private val guild = UUID.randomUUID()
    private val standings = mockk<GuildPrestigeStandings>()
    private val perks = GuildPrestigePerks(standings) { PrestigePerks(PrestigePerksConfig()) }

    private fun tier(t: Int?) = every { standings.standing(guild) } returns t?.let { PrestigeStanding(it, null, 0) }

    @Test
    fun `the public lookup reports the extra stall from Prestige III and 0 on failure`() {
        val lookup = GuildPrestigePerkLookupImpl(perks)
        tier(2)
        assertEquals(0, lookup.extraGuildStalls(guild))
        tier(3)
        assertEquals(1, lookup.extraGuildStalls(guild))
        tier(null)
        assertEquals(0, lookup.extraGuildStalls(guild))
        every { standings.standing(guild) } throws IllegalStateException("db down")
        assertEquals(0, lookup.extraGuildStalls(guild))
    }
}
