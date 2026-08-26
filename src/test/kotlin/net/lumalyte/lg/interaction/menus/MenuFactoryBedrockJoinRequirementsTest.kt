package net.lumalyte.lg.interaction.menus

import io.mockk.every
import io.mockk.mockk
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.PlatformDetectionService
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.bedrock.BedrockJoinRequirementsMenu
import org.bukkit.entity.Player
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import java.util.logging.Logger
import kotlin.test.assertIs

class MenuFactoryBedrockJoinRequirementsTest {
    @Test
    fun `Bedrock player receives the native join requirements form`() {
        val player = mockk<Player>()
        val platform = mockk<PlatformDetectionService>()
        val configService = mockk<ConfigService>()
        every { player.name } returns "BedrockPlayer"
        every { platform.isBedrockPlayer(player) } returns true
        every { platform.isCumulusAvailable() } returns true
        every { configService.loadConfig() } returns MainConfig()
        val factory = MenuFactory(platform, configService, Logger.getAnonymousLogger(), mockk<LangService>())
        val guild = Guild(UUID.randomUUID(), "Test Guild", createdAt = Instant.now())

        val menu = factory.createJoinRequirementsMenu(mockk(), player, guild)

        assertIs<BedrockJoinRequirementsMenu>(menu)
    }
}
