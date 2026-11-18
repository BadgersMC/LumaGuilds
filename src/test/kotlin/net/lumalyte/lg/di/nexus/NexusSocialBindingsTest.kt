package net.lumalyte.lg.di.nexus

import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import net.badgersmc.nexus.core.NexusContext
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.DiscordGuildRoleGateway
import net.lumalyte.lg.application.services.EmojiPermissionGateway
import net.lumalyte.lg.application.services.GuildRecruitmentEligibility
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.PenaltyService
import net.lumalyte.lg.interaction.listeners.ChatInputListener
import net.lumalyte.lg.interaction.listeners.GuildChatListener
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import net.lumalyte.lg.infrastructure.services.GuildRecruitmentEligibilityBukkit
import net.lumalyte.lg.infrastructure.services.LuckPermsEmojiPermissionGateway
import net.lumalyte.lg.infrastructure.services.UnavailableDiscordGuildRoleGateway
import org.bukkit.Bukkit
import org.bukkit.plugin.PluginManager
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NexusSocialBindingsTest {
    private val expectedNames = setOf(
        "guildRecruitmentNotifications", "guildRecruitmentDiscovery", "guildRecruitmentListener",
        "guildRecruitmentRepository", "recruitmentDirectoryRepository", "guildRecruitmentEligibility",
        "guildRecruitmentService", "guildOnboardingRepository", "guildGettingStartedService",
        "guildOnboardingListener", "partyRepository", "playerPartyPreferenceRepository",
        "partyRequestRepository", "chatSettingsRepository", "guildChatRankSettingsRepository",
        "guildChatRankSettingsService", "guildChatReconnectSettingsRepository",
        "guildChatReconnectSettingsService", "guildChatReconnectListener", "partyService", "chatService",
        "guildChannelCreationListener", "guildDisbandedListener", "guildEmojiGrantService",
        "emojiGrantRepository", "emojiPermissionGateway", "guildEmojiGrantReconciler",
        "guildEmojiGrantListener", "guildDiscordRoleRepository", "discordGuildRoleGateway",
        "guildDiscordRoleService", "guildDiscordRoleListener", "roseChatCleanupListener",
        "globalChatVisibilityListener", "chatDestinationIndicator", "inviteBlockChatListener",
        "guildMuteChatListener", "chatInputListener", "guildChatListener",
    )

    private inline fun withContext(block: (NexusContext) -> Unit) {
        val context = NexusContext.create()
        try {
            block(context)
        } finally {
            context.close()
        }
    }

    @Test
    fun `social bindings map 39 of 41 legacy registrations under their contract names`() = withContext { context ->
        context.registerSocialBindings()
        val names = context.getBeanNames().toSet()
        assertEquals(expectedNames, names)
        assertEquals(39, names.size)
        // Discord subscriptions are registered by the separate Nexus Discord group.
        assertFalse("discordAccountLinkSubscription" in names)
        assertFalse("discordGuildProfileSubscription" in names)
    }

    @Test
    fun `chat listeners resolve lazily as constructor-injected singletons`() = withContext { context ->
        // Strict mocks: constructing either listener must not call any dependency.
        val config = mockk<ConfigService>()
        context.registerBean("configService", ConfigService::class, config)
        context.registerBean("guildService", GuildService::class, mockk<GuildService>())
        context.registerBean("memberService", MemberService::class, mockk<MemberService>())
        context.registerBean("penaltyService", PenaltyService::class, mockk<PenaltyService>())
        context.registerBean("langService", LangService::class, mockk<LangService>())
        context.registerSocialBindings()

        val input = context.getBean<ChatInputListener>()
        assertSame(input, context.getBean<ChatInputListener>())
        val guildChat = context.getBean<GuildChatListener>()
        assertSame(guildChat, context.getBean<GuildChatListener>())
        confirmVerified(config)
    }

    @Test
    fun `registration is lazy and touches neither config nor storage nor Bukkit`() = withContext { context ->
        val configReads = AtomicInteger()
        context.registerBean("configService", ConfigService::class) {
            configReads.incrementAndGet()
            mockk<ConfigService>()
        }
        context.registerSocialBindings()
        assertEquals(0, configReads.get())
        assertTrue(expectedNames.all { it in context.getBeanNames() })
    }

    @Test
    fun `no-IO social services resolve as singletons through their contracts`() = withContext { context ->
        context.registerBean("storage", Storage::class, mockk<Storage<*>>())
        context.registerBean("memberService", MemberService::class, mockk<MemberService>())
        context.registerSocialBindings()

        val eligibility = context.getBean<GuildRecruitmentEligibility>()
        assertIs<GuildRecruitmentEligibilityBukkit>(eligibility)
        assertSame(eligibility, context.getBean<GuildRecruitmentEligibility>())

        val gateway = context.getBean<EmojiPermissionGateway>()
        assertIs<LuckPermsEmojiPermissionGateway>(gateway)
        assertSame(gateway, context.getBean<EmojiPermissionGateway>())
    }

    @Test
    fun `Discord role gateway falls back to unavailable when DiscordSRV is absent`() = withContext { context ->
        val plugins = mockk<PluginManager>()
        every { plugins.getPlugin("DiscordSRV") } returns null
        mockkStatic(Bukkit::class)
        try {
            every { Bukkit.getPluginManager() } returns plugins
            context.registerSocialBindings()
            val gateway = context.getBean<DiscordGuildRoleGateway>()
            assertIs<UnavailableDiscordGuildRoleGateway>(gateway)
            assertSame(gateway, context.getBean<DiscordGuildRoleGateway>())
        } finally {
            unmockkStatic(Bukkit::class)
        }
    }
}
