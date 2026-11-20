package net.lumalyte.lg.application.services

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.config.MainConfig
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

/** REQ-179: a player's home travel times come from their guild's best Quick Travel. */
class HomeTravelTest {
    private val guild = UUID.randomUUID()
    private val player = UUID.randomUUID()
    private val progression = mockk<ProgressionService>()
    private val members = mockk<MemberService>()
    private val config = mockk<ConfigService>()
    private val travel = HomeTravel(progression, members, config)

    init {
        every { config.loadConfig() } returns MainConfig().apply { guild.homeTeleportCooldownSeconds = 60 }
    }

    @Test
    fun `Quick Travel 5 gives a 2 second countdown and half the cooldown`() {
        every { members.getPlayerGuilds(player) } returns setOf(guild)
        every { progression.getHomeCooldownMultiplier(guild) } returns 0.5
        assertEquals(2, travel.countdownSeconds(player))
        assertEquals(30L, travel.cooldownSeconds(player))
    }

    @Test
    fun `no guild or a failed read keeps the standard times`() {
        every { members.getPlayerGuilds(player) } returns emptySet()
        assertEquals(5, travel.countdownSeconds(player))
        assertEquals(60L, travel.cooldownSeconds(player))
        every { members.getPlayerGuilds(player) } returns setOf(guild)
        every { progression.getHomeCooldownMultiplier(guild) } throws IllegalStateException("db down")
        assertEquals(5, travel.countdownSeconds(player))
        assertEquals(60L, travel.cooldownSeconds(player))
    }
}
