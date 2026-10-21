package net.lumalyte.lg.interaction.menus

import io.mockk.*
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.*
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.bedrock.BedrockGuildLeaderboardMenu
import net.lumalyte.lg.interaction.menus.guild.GuildLeaderboardDialogMenu
import org.bukkit.entity.Player
import org.junit.jupiter.api.*
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.time.Instant
import java.util.UUID
import java.util.logging.Logger
import kotlin.test.assertIs

internal class MenuFactoryLeaderboardTest {
    @AfterEach fun cleanup() { stopKoin(); unmockkAll() }
    @Test fun leaderboardEntryUsesConfiguredNativePlatform() {
        stopKoin()
        val guilds = mockk<GuildService>(); val members = mockk<MemberService>(); val quests = mockk<QuestService>()
        val lang = mockk<LangService>()
        startKoin { modules(module { single { guilds }; single { members }; single { quests }; single { lang } }) }
        val player = mockk<Player>(); every { player.name } returns "Viewer"
        val platform = mockk<PlatformDetectionService>(); val config = mockk<ConfigService>()
        every { config.loadConfig() } returns MainConfig()
        every { platform.isCumulusAvailable() } returns true
        val factory = MenuFactory(platform, config, Logger.getAnonymousLogger(), lang)
        val guild = Guild(UUID.randomUUID(), "Guild", createdAt = Instant.EPOCH)
        every { platform.isBedrockPlayer(player) } returns false
        assertIs<GuildLeaderboardDialogMenu>(factory.createGuildLeaderboardMenu(mockk(), player, guild))
        every { platform.isBedrockPlayer(player) } returns true
        assertIs<BedrockGuildLeaderboardMenu>(factory.createGuildLeaderboardMenu(mockk(), player, guild))
    }
}
