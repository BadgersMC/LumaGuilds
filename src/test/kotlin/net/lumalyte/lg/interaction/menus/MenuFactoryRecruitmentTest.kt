package net.lumalyte.lg.interaction.menus

import io.mockk.every
import io.mockk.mockk
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.PlatformDetectionService
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.interaction.menus.bedrock.BedrockGuildRecruitmentMenu
import net.lumalyte.lg.interaction.menus.guild.GuildRecruitmentMenu
import org.bukkit.entity.Player
import org.junit.jupiter.api.Test
import java.util.logging.Logger
import kotlin.test.assertIs

internal class MenuFactoryRecruitmentTest {
    @org.junit.jupiter.api.DisplayName("existing LFG entry point routes each platform to its native recruitment menu")
    @Test
    fun existingLfgEntryPointRoute1() {
        val player = mockk<Player>()
        val platform = mockk<PlatformDetectionService>()
        val config = mockk<ConfigService>()
        every { player.name } returns "RecruitmentPlayer"
        every { config.loadConfig() } returns MainConfig()
        every { platform.isCumulusAvailable() } returns true
        val factory = MenuFactory(platform, config, Logger.getAnonymousLogger(), mockk<LangService>())
        every { platform.isBedrockPlayer(player) } returns false
        assertIs<GuildRecruitmentMenu>(factory.createLfgBrowserMenu(mockk(), player))
        every { platform.isBedrockPlayer(player) } returns true
        assertIs<BedrockGuildRecruitmentMenu>(factory.createLfgBrowserMenu(mockk(), player))
    }
}
