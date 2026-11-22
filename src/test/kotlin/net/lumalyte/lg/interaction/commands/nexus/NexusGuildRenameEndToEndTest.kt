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
import net.lumalyte.lg.utils.GuildHomeSafety
import org.bukkit.Location
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

    @Test
    fun `ally home success through alias starts the cooldown for the main home route`() {
        val lang = mockk<LangService>(relaxed = true)
        val guilds = mockk<GuildService>(relaxed = true)
        val config = mockk<ConfigService>()
        every { config.loadConfig() } returns MainConfig().apply { guild.homeTeleportCooldownSeconds = 120 }
        val deps = dependencies(lang, guilds, mockk(), config)
        val playerId = UUID.randomUUID()
        val player = mockk<Player>(relaxed = true)
        every { player.uniqueId } returns playerId
        every { player.hasPermission(any<String>()) } returns true
        val guildId = UUID.randomUUID()
        val guild = mockk<Guild> { every { id } returns guildId }
        every { guilds.getPlayerGuilds(playerId) } returns setOf(guild)
        every { guilds.getHome(guildId, "main") } returns mockk()
        every { deps.guildHomeActivationService.value.isActive(guildId, "main") } returns true
        every { guilds.canUseHome(playerId, guildId, "main") } returns true
        every { deps.progressionService.value.getHomeCooldownMultiplier(guildId) } returns 1.0
        val teleports = deps.teleportationService.value
        every { teleports.startTeleport(player, any(), any()) } answers { thirdArg<() -> Unit>()() }
        val providers = NexusGuildCommandParityTest.suggestionKeys.associateWith {
            SuggestionProvider<CommandSourceStack> { _, builder -> builder.buildFuture() }
        }
        val dispatcher = guildDispatcher(mapOf(GuildCommandHandlers::class to GuildCommandHandlers(deps)), providers).dispatcher
        val source = mockk<CommandSourceStack> { every { sender } returns player }
        GuildHomeSafety.checkAndRemember(player, Location(null, 1.0, 2.0, 3.0))
        kotlin.test.assertEquals(1, dispatcher.execute("g allyhome confirm", source))
        kotlin.test.assertEquals(1, dispatcher.execute("guild home", source))
        verify(exactly = 1) { teleports.startTeleport(player, any(), any()) }
        verify { lang.msg("command.migrated.guild.home.please_wait_s_before_teleporting_again", *anyVararg()) }
        verify(exactly = 0) { teleports.startTeleportGuarded(any(), any(), any(), any()) }
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
