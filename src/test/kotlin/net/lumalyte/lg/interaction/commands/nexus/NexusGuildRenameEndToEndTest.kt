package net.lumalyte.lg.interaction.commands.nexus

import com.mojang.brigadier.suggestion.SuggestionProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.interaction.commands.GuildCommandDependencies
import net.lumalyte.lg.interaction.commands.GuildCommandHandlers
import org.bukkit.entity.Player
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * REQ-NEXUS-CUTOVER-1 end to end: the greedy `/guild rename` route runs the real shared handler,
 * re-checks the member's current rank permission on every call, and persists through GuildService.
 */
class NexusGuildRenameEndToEndTest {

    @Test
    fun `greedy rename persists multi-word names only while the member holds the current permission`() {
        val guildService = mockk<GuildService>(relaxed = true)
        val memberService = mockk<MemberService>()
        val config = mockk<ConfigService>()
        val guild = mockk<Guild>()
        val guildId = UUID.randomUUID()
        val playerId = UUID.randomUUID()
        val player = mockk<Player>(relaxed = true)
        every { player.uniqueId } returns playerId
        every { player.hasPermission(any<String>()) } returns true
        every { guild.id } returns guildId
        every { guildService.getPlayerGuilds(playerId) } returns setOf(guild)
        every { guildService.renameGuild(guildId, any(), playerId) } returns true
        every { config.loadConfig() } returns MainConfig()
        var allowed = true
        every { memberService.hasPermission(playerId, guildId, RankPermission.MANAGE_GUILD_SETTINGS) } answers { allowed }

        val handlers = GuildCommandHandlers(
            dependencies(lang = mockk(relaxed = true), guildService = guildService, memberService = memberService, config = config),
        )
        val providers = NexusGuildCommandParityTest.suggestionKeys.associateWith {
            SuggestionProvider<CommandSourceStack> { _, builder -> builder.buildFuture() }
        }
        val dispatcher = guildDispatcher(
            mapOf(GuildCommandHandlers::class to handlers, LangService::class to mockk<LangService>(relaxed = true)),
            providers,
        ).dispatcher
        val source = mockk<CommandSourceStack>().also { every { it.sender } returns player }

        dispatcher.execute("guild rename Iron Order of Dawn", source)
        verify(exactly = 1) { guildService.renameGuild(guildId, "Iron Order of Dawn", playerId) }

        allowed = false // demoted between commands: authorization is current-state, not cached
        dispatcher.execute("guild rename Another Name", source)
        verify(exactly = 0) { guildService.renameGuild(guildId, "Another Name", playerId) }
    }

    private fun dependencies(
        lang: LangService,
        guildService: GuildService,
        memberService: MemberService,
        config: ConfigService,
    ) = GuildCommandDependencies(
        lang = lazyOf(lang),
        guildService = lazyOf(guildService),
        lfgService = lazy { mockk(relaxed = true) },
        guildRepository = lazy { mockk(relaxed = true) },
        rankService = lazy { mockk(relaxed = true) },
        chatRankSettings = lazy { mockk(relaxed = true) },
        memberService = lazyOf(memberService),
        inviteBlocks = lazy { mockk(relaxed = true) },
        vaultService = lazy { mockk(relaxed = true) },
        warService = lazy { mockk(relaxed = true) },
        configService = lazyOf(config),
        menuFactory = lazy { mockk(relaxed = true) },
        progressionService = lazy { mockk(relaxed = true) },
        historyRepository = lazy { mockk(relaxed = true) },
        guildChatListener = lazy { mockk(relaxed = true) },
        adminOverrideService = lazy { mockk(relaxed = true) },
        teleportationService = lazy { mockk(relaxed = true) },
        bannermanListeners = lazy { mockk(relaxed = true) },
        strikeService = lazy { mockk(relaxed = true) },
        bankService = lazy { mockk(relaxed = true) },
        guildCostService = lazy { mockk(relaxed = true) },
        guildHomeActivationService = lazy { mockk(relaxed = true) },
        warBannerService = lazy { mockk(relaxed = true) },
        guildLoginNotificationService = lazy { mockk(relaxed = true) },
        relationService = lazy { mockk(relaxed = true) },
        nexoEmojiService = lazy { mockk(relaxed = true) },
        guildRecruitmentService = lazy { mockk(relaxed = true) },
        guildOnboardingRepository = lazy { mockk(relaxed = true) },
    )
}
