package net.lumalyte.lg.di.nexus

import co.aikar.idb.Database
import net.badgersmc.nexus.annotations.ScopeType
import net.badgersmc.nexus.core.NexusContext
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.persistence.ChatSettingsRepository
import net.lumalyte.lg.application.persistence.EmojiGrantRepository
import net.lumalyte.lg.application.persistence.GuildChatRankSettingsRepository
import net.lumalyte.lg.application.persistence.GuildChatReconnectSettingsRepository
import net.lumalyte.lg.application.persistence.GuildDiscordRoleRepository
import net.lumalyte.lg.application.persistence.GuildOnboardingRepository
import net.lumalyte.lg.application.persistence.GuildRecruitmentRepository
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.persistence.PartyRepository
import net.lumalyte.lg.application.persistence.PartyRequestRepository
import net.lumalyte.lg.application.persistence.PlayerPartyPreferenceRepository
import net.lumalyte.lg.application.persistence.RecruitmentDirectoryRepository
import net.lumalyte.lg.application.services.ChatService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.DiscordGuildRoleGateway
import net.lumalyte.lg.application.services.EmojiPermissionGateway
import net.lumalyte.lg.application.services.GuildChatRankSettingsService
import net.lumalyte.lg.application.services.GuildChatReconnectSettingsService
import net.lumalyte.lg.application.services.GuildDiscordRoleService
import net.lumalyte.lg.application.services.GuildEmojiGrantReconciler
import net.lumalyte.lg.application.services.GuildGettingStartedService
import net.lumalyte.lg.application.services.GuildRecruitmentEligibility
import net.lumalyte.lg.application.services.GuildRecruitmentListings
import net.lumalyte.lg.application.services.GuildRecruitmentRules
import net.lumalyte.lg.application.services.GuildRecruitmentService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.InviteBlockService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.PartyService
import net.lumalyte.lg.application.services.PenaltyService
import net.lumalyte.lg.application.services.QuestService
import net.lumalyte.lg.infrastructure.listeners.ChatDestinationIndicator
import net.lumalyte.lg.infrastructure.listeners.GlobalChatVisibilityListener
import net.lumalyte.lg.infrastructure.listeners.GuildChannelCreationListener
import net.lumalyte.lg.infrastructure.listeners.GuildChatReconnectListener
import net.lumalyte.lg.infrastructure.listeners.GuildDisbandedListener
import net.lumalyte.lg.infrastructure.listeners.GuildDiscordRoleListener
import net.lumalyte.lg.infrastructure.listeners.GuildEmojiGrantListener
import net.lumalyte.lg.infrastructure.listeners.GuildMuteChatListener
import net.lumalyte.lg.infrastructure.listeners.GuildOnboardingListener
import net.lumalyte.lg.infrastructure.listeners.GuildRecruitmentDiscovery
import net.lumalyte.lg.infrastructure.listeners.GuildRecruitmentListener
import net.lumalyte.lg.infrastructure.listeners.GuildRecruitmentNotifications
import net.lumalyte.lg.infrastructure.listeners.InviteBlockChatListener
import net.lumalyte.lg.infrastructure.listeners.RoseChatCleanupListener
import net.lumalyte.lg.infrastructure.persistence.guilds.ChatSettingsRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.guilds.EmojiGrantRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.guilds.GuildChatRankSettingsRepositorySQL
import net.lumalyte.lg.infrastructure.persistence.guilds.GuildChatReconnectSettingsRepositorySQL
import net.lumalyte.lg.infrastructure.persistence.guilds.GuildDiscordRoleRepositorySQL
import net.lumalyte.lg.infrastructure.persistence.guilds.GuildOnboardingRepositorySQL
import net.lumalyte.lg.infrastructure.persistence.guilds.GuildRecruitmentRepositorySQL
import net.lumalyte.lg.infrastructure.persistence.guilds.PartyRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.guilds.PartyRequestRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.guilds.PlayerPartyPreferenceRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.guilds.RecruitmentDirectoryRepositorySQL
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import net.lumalyte.lg.infrastructure.services.ChatServiceBukkit
import net.lumalyte.lg.infrastructure.services.DiscordSrvGuildRoleGateway
import net.lumalyte.lg.infrastructure.services.GuildEmojiGrantService
import net.lumalyte.lg.infrastructure.services.GuildRecruitmentEligibilityBukkit
import net.lumalyte.lg.infrastructure.services.LuckPermsEmojiPermissionGateway
import net.lumalyte.lg.infrastructure.services.PartyServiceBukkit
import net.lumalyte.lg.infrastructure.services.UnavailableDiscordGuildRoleGateway
import net.lumalyte.lg.interaction.listeners.ChatInputListener
import net.lumalyte.lg.interaction.listeners.GuildChatListener
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import kotlin.reflect.KClass

/**
 * Lazy Nexus replacements for the legacy socialModule Koin graph (39 of 41).
 *
 * Every factory is a SINGLETON and runs only on first lookup, so no listener,
 * repository (DB init) or optional-plugin probe executes at registration time.
 *
 * Not mapped here:
 *  - DiscordAccountLinkSubscription / DiscordGuildProfileSubscription: registered
 *    separately by NexusDiscordBindings, borrowing the Nexus-owned virtual executor.
 */
internal fun NexusContext.registerSocialBindings() {
    // Recruitment
    socialSingleton<GuildRecruitmentNotifications> {
        GuildRecruitmentNotifications(getBean(), getBean(), getBean(), getBean(), getBean<LangService>())
    }
    socialSingleton<GuildRecruitmentDiscovery> {
        GuildRecruitmentDiscovery(getBean(), getBean(), getBean(), getBean<LangService>())
    }
    socialSingleton<GuildRecruitmentListener> {
        GuildRecruitmentListener(
            plugin = getBean<Plugin>(),
            recruitment = getBean(),
            config = getBean(),
            notifications = getBean(),
            discovery = getBean(),
            directory = getBean<RecruitmentDirectoryRepository>(),
            members = getBean<MemberService>(),
            guilds = getBean<GuildService>(),
            quests = getBean<QuestService>(),
            lang = getBean<LangService>(),
        )
    }
    socialSingleton<GuildRecruitmentRepository> { GuildRecruitmentRepositorySQL(socialStorage()) }
    socialSingleton<RecruitmentDirectoryRepository> {
        RecruitmentDirectoryRepositorySQL(socialStorage(), getBean<GuildRepository>())
    }
    socialSingleton<GuildRecruitmentEligibility> {
        GuildRecruitmentEligibilityBukkit(socialStorage(), getBean<MemberService>())
    }
    socialSingleton<GuildRecruitmentService> {
        val config = getBean<ConfigService>()
        val eligibility = getBean<GuildRecruitmentEligibility>()
        val listings = GuildRecruitmentListings(
            getBean(), getBean(), getBean(), maximumMembers = { config.loadConfig().guild.maxMembersPerGuild },
        )
        val rules = GuildRecruitmentRules({ config.loadConfig().recruitment.policy() }, eligibility::allows)
        GuildRecruitmentService(getBean(), listings, getBean(), getBean(), getBean(), rules)
    }

    // Onboarding
    socialSingleton<GuildOnboardingRepository> { GuildOnboardingRepositorySQL(socialStorage()) }
    socialSingleton<GuildGettingStartedService> { GuildGettingStartedService(getBean(), getBean(), getBean()) }
    socialSingleton<GuildOnboardingListener> {
        GuildOnboardingListener(getBean<Plugin>(), getBean(), getBean(), getBean(), getBean<LangService>())
    }

    // Repositories
    socialSingleton<PartyRepository> { PartyRepositorySQLite(socialStorage()) }
    socialSingleton<PlayerPartyPreferenceRepository> { PlayerPartyPreferenceRepositorySQLite(socialStorage()) }
    socialSingleton<PartyRequestRepository> { PartyRequestRepositorySQLite(socialStorage()) }
    socialSingleton<ChatSettingsRepository> {
        ChatSettingsRepositorySQLite(
            socialStorage(),
            getBean<ConfigService>().loadConfig().chat.defaultChannelVisibility,
        )
    }
    socialSingleton<GuildChatRankSettingsRepository> { GuildChatRankSettingsRepositorySQL(socialStorage()) }
    socialSingleton<GuildChatRankSettingsService> { GuildChatRankSettingsService(getBean(), getBean()) }
    socialSingleton<GuildChatReconnectSettingsRepository> {
        GuildChatReconnectSettingsRepositorySQL(socialStorage())
    }
    socialSingleton<GuildChatReconnectSettingsService> { GuildChatReconnectSettingsService(getBean(), getBean()) }
    socialSingleton<GuildChatReconnectListener> {
        GuildChatReconnectListener(getBean<Plugin>(), getBean(), getBean<LangService>())
    }

    // Services
    socialSingleton<PartyService> {
        PartyServiceBukkit(getBean(), getBean(), getBean(), getBean(), getBean<LangService>())
    }
    socialSingleton<ChatService> {
        ChatServiceBukkit(
            getBean(), getBean(), getBean(), getBean(), getBean(), getBean(),
            getBean(), getBean(), getBean(), getBean(), getBean<LangService>(), getBean(),
            inviteBlocks = getBean<InviteBlockService>(),
        )
    }

    // Listeners and emoji grants
    socialSingleton<GuildChannelCreationListener> { GuildChannelCreationListener(getBean(), getBean()) }
    socialSingleton<GuildDisbandedListener> {
        GuildDisbandedListener(getBean(), getBean(), getBean<LangService>(), getBean())
    }
    socialSingleton<GuildEmojiGrantService> { GuildEmojiGrantService(getBean(), getBean(), getBean()) }
    socialSingleton<EmojiGrantRepository> { EmojiGrantRepositorySQLite(socialStorage()) }
    socialSingleton<EmojiPermissionGateway> { LuckPermsEmojiPermissionGateway() }
    socialSingleton<GuildEmojiGrantReconciler> { GuildEmojiGrantReconciler(getBean(), getBean(), getBean(), getBean()) }
    socialSingleton<GuildEmojiGrantListener> { GuildEmojiGrantListener(getBean()) }

    // Discord roles: DiscordSRV is optional; probe only on first lookup, like Koin.
    socialSingleton<GuildDiscordRoleRepository> { GuildDiscordRoleRepositorySQL(socialStorage()) }
    socialSingleton<DiscordGuildRoleGateway> {
        if (Bukkit.getPluginManager().getPlugin("DiscordSRV") != null) {
            DiscordSrvGuildRoleGateway()
        } else {
            UnavailableDiscordGuildRoleGateway()
        }
    }
    socialSingleton<GuildDiscordRoleService> {
        GuildDiscordRoleService(getBean(), getBean(), getBean(), getBean(), getBean(), getBean(), getBean())
    }
    socialSingleton<GuildDiscordRoleListener> { GuildDiscordRoleListener(getBean()) }

    // RoseChat-facing listeners keep their default RealRoseChatAdapter, built lazily.
    socialSingleton<RoseChatCleanupListener> {
        RoseChatCleanupListener(getBean(), getBean(), getBean(), getBean(), getBean<LangService>())
    }
    socialSingleton<GlobalChatVisibilityListener> { GlobalChatVisibilityListener(getBean()) }
    socialSingleton<ChatDestinationIndicator> { ChatDestinationIndicator(getBean(), getBean<LangService>()) }
    socialSingleton<InviteBlockChatListener> { InviteBlockChatListener(getBean<InviteBlockService>()) }
    socialSingleton<GuildMuteChatListener> { GuildMuteChatListener(getBean(), getBean(), getBean<LangService>()) }

    // Interaction chat listeners: one singleton each so prompt and previous-channel state stay shared.
    socialSingleton<ChatInputListener> { ChatInputListener(getBean<ConfigService>()) }
    socialSingleton<GuildChatListener> {
        GuildChatListener(
            getBean<GuildService>(),
            getBean<MemberService>(),
            getBean<PenaltyService>(),
            getBean<LangService>(),
        )
    }
}

@Suppress("UNCHECKED_CAST")
private fun NexusContext.socialStorage(): Storage<Database> = getBean<Storage<*>>() as Storage<Database>

private inline fun <reified T : Any> NexusContext.socialSingleton(noinline factory: () -> T) {
    registerBean(socialName(T::class), T::class, ScopeType.SINGLETON, factory)
}

private fun socialName(type: KClass<*>): String =
    requireNotNull(type.simpleName).replaceFirstChar { it.lowercase() }
