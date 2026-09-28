package net.lumalyte.lg.interaction.menus.bedrock

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.entities.RankPermission
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BedrockGuildAuthorizationTest {
    private val playerId = UUID.randomUUID()
    private val guildId = UUID.randomUUID()
    private val guildService = mockk<GuildService>()
    private val authorization = BedrockGuildAuthorization(guildService)

    @Test
    fun `ordinary member cannot mutate management surfaces`() {
        every { guildService.hasPermission(playerId, guildId, RankPermission.MANAGE_BANK_SETTINGS) } returns false
        every { guildService.hasPermission(playerId, guildId, RankPermission.MANAGE_GUILD_SETTINGS) } returns false
        every { guildService.hasPermission(playerId, guildId, RankPermission.MANAGE_RANKS) } returns false

        assertFalse(authorization.canManageBankSettings(playerId, guildId))
        assertFalse(authorization.canManageGuildSettings(playerId, guildId))
        assertFalse(authorization.canManageRanks(playerId, guildId))
    }

    @Test
    fun `authorized member can mutate the matching management surface`() {
        every { guildService.hasPermission(playerId, guildId, RankPermission.MANAGE_BANK_SETTINGS) } returns true
        every { guildService.hasPermission(playerId, guildId, RankPermission.MANAGE_GUILD_SETTINGS) } returns true
        every { guildService.hasPermission(playerId, guildId, RankPermission.MANAGE_RANKS) } returns true

        assertTrue(authorization.canManageBankSettings(playerId, guildId))
        assertTrue(authorization.canManageGuildSettings(playerId, guildId))
        assertTrue(authorization.canManageRanks(playerId, guildId))
    }
}