package net.lumalyte.lg.interaction.menus

import io.mockk.every
import io.mockk.mockk
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.PlatformDetectionService
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RelationType
import net.lumalyte.lg.interaction.menus.bedrock.BedrockGuildRelationBrowserMenu
import net.lumalyte.lg.interaction.menus.guild.GuildRelationBrowserMenu
import org.bukkit.entity.Player
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import java.util.logging.Logger
import kotlin.test.assertIs

class MenuFactoryGuildRelationBrowserTest {
    private val guild = Guild(UUID.randomUUID(), "Relations", createdAt = Instant.EPOCH)

    @Test
    fun `Bedrock guild info relation browser uses native form menu`() {
        val player = mockk<Player>()
        val platform = mockk<PlatformDetectionService>()
        val configService = mockk<ConfigService>()
        every { player.name } returns "BedrockPlayer"
        every { platform.isBedrockPlayer(player) } returns true
        every { platform.isCumulusAvailable() } returns true
        every { configService.loadConfig() } returns MainConfig()
        val factory = MenuFactory(platform, configService, Logger.getAnonymousLogger(), mockk<LangService>())

        val menu = factory.createGuildRelationBrowserMenu(mockk(), player, guild, RelationType.ALLY)

        assertIs<BedrockGuildRelationBrowserMenu>(menu)
    }

    @Test
    fun `Java guild info relation browser uses inventory menu`() {
        val player = mockk<Player>()
        val platform = mockk<PlatformDetectionService>()
        val configService = mockk<ConfigService>()
        every { player.name } returns "JavaPlayer"
        every { platform.isBedrockPlayer(player) } returns false
        every { configService.loadConfig() } returns MainConfig()
        val factory = MenuFactory(platform, configService, Logger.getAnonymousLogger(), mockk<LangService>())

        val menu = factory.createGuildRelationBrowserMenu(mockk(), player, guild, RelationType.ENEMY)

        assertIs<GuildRelationBrowserMenu>(menu)
    }
}
