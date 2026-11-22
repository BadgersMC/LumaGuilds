package net.lumalyte.lg.interaction

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.actions.player.ToggleClaimOverride
import net.lumalyte.lg.application.persistence.BlockProvenanceRepository
import net.lumalyte.lg.application.persistence.GuildOnboardingRepository
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.results.player.ToggleClaimOverrideResult
import net.lumalyte.lg.application.services.AdminOverrideService
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildRolePermissionResolver
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.LeaderboardService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.PlaytimeActivityService
import net.lumalyte.lg.application.services.ProgressionService
import net.lumalyte.lg.application.services.apollo.LunarClientService
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.infrastructure.listeners.BlockProvenanceOperationQueue
import net.lumalyte.lg.infrastructure.listeners.ProgressionEventListener
import net.lumalyte.lg.infrastructure.listeners.apollo.GuildTeamListener
import net.lumalyte.lg.infrastructure.placeholders.LumaGuildsExpansion
import net.lumalyte.lg.infrastructure.services.AsyncTaskService
import net.lumalyte.lg.infrastructure.services.LumaGuildsChannel
import net.lumalyte.lg.infrastructure.services.LumaGuildsChannelDependencies
import net.lumalyte.lg.infrastructure.services.LumaGuildsChannelDependencySource
import net.lumalyte.lg.infrastructure.services.ProgressionConfigService
import net.lumalyte.lg.infrastructure.services.apollo.GuildNotificationService
import net.lumalyte.lg.infrastructure.services.apollo.GuildTeamService
import net.lumalyte.lg.interaction.commands.ClaimCommandDependencies
import net.lumalyte.lg.interaction.commands.ClaimOverrideCommand
import net.lumalyte.lg.interaction.commands.GuildCommand
import net.lumalyte.lg.interaction.commands.LumaGuildsCommand
import net.lumalyte.lg.interaction.commands.QuickModChatCommand
import net.lumalyte.lg.interaction.commands.RenameCommand
import net.lumalyte.lg.interaction.commands.admin.BankCreditCommand
import net.lumalyte.lg.interaction.listeners.VaultInventoryListener
import dev.rosewood.rosechat.hook.channel.ChannelProvider
import org.bukkit.GameMode
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.Plugin
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executor
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Focused behavior tests per family: the injected instance is used and nothing resolves eagerly. */
class ListenersWorkerBehaviorTest {
    private val lang = mockk<LangService>(relaxed = true)
    private val playerId = UUID.randomUUID()
    private val player = mockk<Player>(relaxed = true).also { every { it.uniqueId } returns playerId }

    // --- ACF command base + subclasses -------------------------------------------------------

    @Test
    fun claimSubcommandsShareTheExplicitClaimBaseBundleLazily() {
        val wiring = ExplicitWiring(mapOf(LangService::class to lang))
        val dependencies = wiring.build(ClaimCommandDependencies::class)
        val rename = RenameCommand(dependencies, lazy { mockk(relaxed = true) })
        assertEquals(emptyList(), wiring.initialized())
        assertFalse(dependencies.lang.isInitialized())
        assertTrue(rename is net.lumalyte.lg.interaction.commands.ClaimCommand)
    }

    @Test
    fun claimOverrideUsesInjectedToggleOnlyWhenInvoked() {
        val toggle = mockk<ToggleClaimOverride>()
        every { toggle.execute(playerId) } returns ToggleClaimOverrideResult.Success(true)
        val toggleLazy = lazy { toggle }
        val langLazy = lazy { lang }
        val command = ClaimOverrideCommand(langLazy, toggleLazy)
        assertFalse(toggleLazy.isInitialized())
        assertFalse(langLazy.isInitialized())

        command.onClaimOverride(player)

        verify(exactly = 1) { toggle.execute(playerId) }
        verify(exactly = 1) { lang.msg("command.claim_override.enabled") }
    }

    // --- Quick chat commands ------------------------------------------------------------------

    @Test
    fun quickModChatGuardsMembershipThroughInjectedGuildService() {
        val guilds = mockk<GuildService>()
        every { guilds.getPlayerGuilds(playerId) } returns emptySet()
        val guildLazy = lazy { guilds }
        val command = QuickModChatCommand(lazy { lang }, guildLazy, lazy { mockk<MemberService>() })
        assertFalse(guildLazy.isInitialized())

        command.onDefault(player)

        verify(exactly = 1) { guilds.getPlayerGuilds(playerId) }
        verify(exactly = 1) { lang.msg("guild.not_member") }
    }

    // --- Guild command (large bundle) -----------------------------------------------------------

    @Test
    fun guildCommandDismissUsesInjectedOnboardingRepositoryLazily() {
        val guildId = UUID.randomUUID()
        val guilds = mockk<GuildService>()
        every { guilds.getPlayerGuilds(playerId) } returns setOf(Guild(guildId, "Vegas", createdAt = Instant.EPOCH))
        val onboarding = mockk<GuildOnboardingRepository>()
        every { onboarding.dismiss(playerId, guildId) } returns true
        val wiring = ExplicitWiring(
            mapOf(
                LangService::class to lang,
                GuildService::class to guilds,
                GuildOnboardingRepository::class to onboarding,
            ),
        )
        val command = wiring.build(GuildCommand::class)
        assertEquals(emptyList(), wiring.initialized(), "GuildCommand must not resolve collaborators at construction")

        command.onDismissGettingStarted(player)

        verify(exactly = 1) { onboarding.dismiss(playerId, guildId) }
        verify(exactly = 1) { lang.msg("onboarding.dismissed") }
        assertFalse(wiring.lazies.single { it.first.endsWith(".bankService") }.second.isInitialized())
    }

    // --- LumaGuildsCommand: optional providers --------------------------------------------------

    @Test
    fun lumaGuildsOverrideResolvesOptionalClaimRoleResolverAtUseTime() {
        val admin = mockk<AdminOverrideService>()
        every { admin.toggleOverride(playerId) } returns true
        every { player.hasPermission("lumaguilds.admin.bypass") } returns true
        val wiring = ExplicitWiring(mapOf(LangService::class to lang, AdminOverrideService::class to admin))
        val command = wiring.build(LumaGuildsCommand::class)
        assertEquals(emptyMap(), wiring.providerCalls)

        command.onCommand(player, mockk<Command>(relaxed = true), "lumaguilds", arrayOf("override"))

        verify(exactly = 1) { admin.toggleOverride(playerId) }
        assertEquals(1, wiring.providerCalls.filterKeys { it.endsWith("guildRolePermissionResolver") }.values.single())
        verify(exactly = 1) { lang.msg("admin.migrated.luma_guilds.handleoverride.admin_guild_override_enabled") }
    }

    @Test
    fun lumaGuildsOverrideInvalidatesInjectedResolverWhenClaimsEnabled() {
        val admin = mockk<AdminOverrideService>()
        every { admin.toggleOverride(playerId) } returns false
        every { player.hasPermission("lumaguilds.admin.bypass") } returns true
        val resolver = mockk<GuildRolePermissionResolver>(relaxed = true)
        val wiring = ExplicitWiring(
            mapOf(
                LangService::class to lang,
                AdminOverrideService::class to admin,
                GuildRolePermissionResolver::class to resolver,
            ),
        )
        wiring.build(LumaGuildsCommand::class)
            .onCommand(player, mockk<Command>(relaxed = true), "lumaguilds", arrayOf("override"))
        verify(exactly = 1) { resolver.invalidatePlayerCache(playerId) }
    }

    @Test
    fun lumaGuildsProgressionReloadRefreshesInjectedListenerProvider() {
        val listener = mockk<ProgressionEventListener>(relaxed = true)
        val config = mockk<ProgressionConfigService>(relaxed = true)
        val wiring = ExplicitWiring(
            mapOf(
                LangService::class to lang,
                ProgressionEventListener::class to listener,
                ProgressionConfigService::class to config,
            ),
        )
        val console = mockk<CommandSender>(relaxed = true)
        wiring.build(LumaGuildsCommand::class)
            .onCommand(console, mockk<Command>(relaxed = true), "lumaguilds", arrayOf("progressionreload"))
        verify(exactly = 1) { config.reloadProgressionConfig() }
        verify(exactly = 1) { listener.refreshCaches() }
    }

    // --- Admin commands -----------------------------------------------------------------------

    @Test
    fun bankCreditResolvesLangOnlyWhenReplying() {
        val langLazy = lazy { lang }
        val command = BankCreditCommand(mockk<BankService>(), langLazy)
        assertFalse(langLazy.isInitialized())
        val sender = mockk<CommandSender>(relaxed = true)
        every { sender.hasPermission(any<String>()) } returns false
        command.onCommand(sender, mockk(relaxed = true), "bankcredit", emptyArray())
        assertTrue(langLazy.isInitialized())
        verify(exactly = 1) { sender.sendMessage(any<Component>()) }
    }

    // --- Listeners ----------------------------------------------------------------------------

    @Test
    fun guildTeamListenerResolvesOptionalNotificationServiceOnJoin() {
        val members = mockk<MemberService>()
        every { members.getPlayerGuilds(playerId) } returns emptySet()
        val notifications = mockk<GuildNotificationService>(relaxed = true)
        every { notifications.sendWelcomeNotification(player) } returns Unit
        var lookups = 0
        val listener = GuildTeamListener(
            guildTeamService = mockk<GuildTeamService>(relaxed = true),
            memberService = members,
            guildWaypointService = null,
            guildNotificationService = { lookups++; notifications },
        )
        assertEquals(0, lookups)
        val event = mockk<PlayerJoinEvent>()
        every { event.player } returns player

        listener.onPlayerJoin(event)

        assertEquals(1, lookups)
        verify(exactly = 1) { notifications.sendWelcomeNotification(player) }
    }

    @Test
    fun guildTeamListenerToleratesAbsentNotificationService() {
        val members = mockk<MemberService>()
        every { members.getPlayerGuilds(playerId) } returns emptySet()
        val listener = GuildTeamListener(mockk(relaxed = true), members, null) { null }
        val event = mockk<PlayerJoinEvent>()
        every { event.player } returns player
        listener.onPlayerJoin(event)
        verify(exactly = 1) { members.getPlayerGuilds(playerId) }
    }

    @Test
    fun progressionListenerUsesInjectedOptionalLunarProviderLazily() {
        val lunar = mockk<LunarClientService>()
        every { lunar.isLunarClient(player) } returns true
        var lookups = 0
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val listener = progressionListener(scope) { lookups++; lunar }
        try {
            assertEquals(0, lookups, "optional Lunar Client service must not resolve at construction")
            val multiplier = ProgressionEventListener::class.java.getDeclaredMethod("lunarMultiplier", Player::class.java)
                .apply { isAccessible = true }
            assertEquals(2, multiplier.invoke(listener, player))
            assertEquals(1, lookups)
        } finally {
            listener.shutdown()
            scope.cancel()
        }
    }

    @Test
    fun progressionListenerFallsBackWhenLunarProviderIsAbsent() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val listener = progressionListener(scope) { null }
        try {
            val multiplier = ProgressionEventListener::class.java.getDeclaredMethod("lunarMultiplier", Player::class.java)
                .apply { isAccessible = true }
            assertEquals(1, multiplier.invoke(listener, player))
        } finally {
            listener.shutdown()
            scope.cancel()
        }
    }

    @Test
    fun vaultInventoryListenerDefersBankAndLangUntilUse() {
        val wiring = ExplicitWiring(mapOf(MainConfig::class to MainConfig()))
        wiring.build(VaultInventoryListener::class)
        assertTrue(wiring.lazies.map { it.first }.containsAll(
            listOf("VaultInventoryListener.bankService", "VaultInventoryListener.lang"),
        ))
        assertEquals(emptyList(), wiring.initialized())
    }

    // --- Placeholder expansion and services ---------------------------------------------------

    @Test
    fun placeholderExpansionReadsInjectedServicesOnRequest() {
        val guildId = UUID.randomUUID()
        val members = mockk<MemberService>()
        every { members.getPlayerGuilds(playerId) } returns setOf(guildId)
        val guilds = mockk<GuildService>()
        every { guilds.getGuild(guildId) } returns Guild(guildId, "Vegas", createdAt = Instant.EPOCH)
        val wiring = ExplicitWiring(mapOf(MemberService::class to members, GuildService::class to guilds))
        val expansion = wiring.build(LumaGuildsExpansion::class)
        assertEquals(emptyList(), wiring.initialized())

        assertEquals("Vegas", expansion.onPlaceholderRequest(player, "guild_name"))
        verify(exactly = 1) { guilds.getGuild(guildId) }
    }

    @Test
    fun roseChatChannelReceivesDependenciesFromItsExplicitProvider() {
        val wiring = ExplicitWiring()
        val dependencies = wiring.build(LumaGuildsChannelDependencies::class)
        val provider = mockk<ChannelProvider>(
            relaxed = true,
            moreInterfaces = arrayOf(LumaGuildsChannelDependencySource::class),
        )
        every { (provider as LumaGuildsChannelDependencySource).channelDependencies } returns dependencies

        LumaGuildsChannel(provider)

        assertEquals(emptyList(), wiring.initialized())
        verify(exactly = 1) { (provider as LumaGuildsChannelDependencySource).channelDependencies }
    }

    @Test
    fun roseChatChannelFailsClosedWithoutExplicitDependencies() {
        val error = assertFailsWith<IllegalStateException> { LumaGuildsChannel(mockk<ChannelProvider>(relaxed = true)) }
        assertTrue(error.message!!.contains("LumaGuildsChannelDependencySource"))
    }

    private fun progressionListener(scope: CoroutineScope, lunar: () -> LunarClientService?): ProgressionEventListener {
        val configService = object : ConfigService {
            override fun loadConfig(): MainConfig = MainConfig()
        }
        val memberRepository = mockk<MemberRepository>()
        every { memberRepository.getAll() } returns emptySet()
        val plugin = mockk<Plugin>(relaxed = true)
        every { plugin.name } returns "LumaGuilds"
        every { player.gameMode } returns GameMode.SURVIVAL
        return ProgressionEventListener(
            progressionService = mockk<ProgressionService>(relaxed = true),
            memberService = mockk<MemberService>(relaxed = true),
            memberRepository = memberRepository,
            configService = configService,
            asyncTaskService = mockk<AsyncTaskService>(relaxed = true),
            leaderboardService = mockk<LeaderboardService>(relaxed = true),
            playtimeActivityService = object : PlaytimeActivityService {
                override fun isXpBlocked(playerId: UUID): Boolean = false
            },
            blockProvenanceRepository = mockk<BlockProvenanceRepository>(relaxed = true),
            plugin = plugin,
            virtualDispatcher = Dispatchers.Unconfined,
            taskScope = scope,
            provenanceOperations = BlockProvenanceOperationQueue(Executor { it.run() }),
            lunarClientService = lunar,
        )
    }
}
