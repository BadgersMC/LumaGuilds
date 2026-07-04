package net.lumalyte.lg.interaction.commands

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.entities.Guild
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@Suppress("FunctionNaming")
internal class QuickChatGuardMembershipTest {

    private val guildService = mockk<GuildService>()
    private val player = mockk<Player>(relaxed = true)
    private val playerId = UUID.randomUUID()
    private val guildA = Guild(id = UUID.randomUUID(), name = "A", createdAt = Instant.now())

    init {
        every { player.uniqueId } returns playerId
    }

    @Test
    fun `requireGuildMembership — has guilds returns true`() {
        every { guildService.getPlayerGuilds(playerId) } returns setOf(guildA)
        assertTrue(player.requireGuildMembership(guildService))
    }

    @Test
    fun `requireGuildMembership — no guilds returns false`() {
        every { guildService.getPlayerGuilds(playerId) } returns emptySet()
        assertFalse(player.requireGuildMembership(guildService))
    }
}
