package net.lumalyte.lg.interaction

/** Transitional legacy test fixtures; all service reads remain deferred until the original first use. */
internal fun testListenerLumaGuildsExpansion(): net.lumalyte.lg.infrastructure.placeholders.LumaGuildsExpansion =
    net.lumalyte.lg.infrastructure.placeholders.LumaGuildsExpansion(
        dependencies = testListenerLumaGuildsExpansionDependencies(),
    )


internal fun testListenerLumaGuildsExpansionDependencies(): net.lumalyte.lg.infrastructure.placeholders.LumaGuildsExpansionDependencies =
    net.lumalyte.lg.infrastructure.placeholders.LumaGuildsExpansionDependencies(
        guildService = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.application.services.GuildService>() },
        memberService = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.application.services.MemberService>() },
        bankService = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.application.services.BankService>() },
        killService = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.application.services.KillService>() },
        rankService = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.application.services.RankService>() },
        warService = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.application.services.WarService>() },
        relationService = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.application.services.RelationService>() },
        progressionRepository = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.application.persistence.ProgressionRepository>() },
        progressionService = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.application.services.ProgressionService>() },
        configService = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.application.services.ConfigService>() },
        leaderboardRepository = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.application.persistence.LeaderboardRepository>() },
        nexoEmojiService = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.infrastructure.services.NexoEmojiService>() },
        questService = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.application.services.QuestService>() },
        storage = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.infrastructure.persistence.storage.Storage<co.aikar.idb.Database>>() },
        seasonalElo = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.infrastructure.services.SeasonalEloCoordinator>() },
    )


internal fun testListenerLumaGuildsChannelDependencies(): net.lumalyte.lg.infrastructure.services.LumaGuildsChannelDependencies =
    net.lumalyte.lg.infrastructure.services.LumaGuildsChannelDependencies(
        memberService = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.application.services.MemberService>() },
        guildService = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.application.services.GuildService>() },
        relationService = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.application.services.RelationService>() },
        chatSettingsRepository = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.application.persistence.ChatSettingsRepository>() },
        rankSettings = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.application.services.GuildChatRankSettingsService>() },
        rankService = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.application.services.RankService>() },
    )


internal fun testListenerFullscreenAnnounceCommand(): net.lumalyte.lg.interaction.commands.FullscreenAnnounceCommand =
    net.lumalyte.lg.interaction.commands.FullscreenAnnounceCommand(
        guilds = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.application.services.GuildService>() },
        members = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.application.services.MemberService>() },
        service = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.application.services.ChatService>() },
        penalties = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.application.services.PenaltyService>() },
        lang = lazy { org.koin.core.context.GlobalContext.get().get<net.badgersmc.nexus.i18n.LangService>() },
    )


internal fun testListenerGuildCommand(): net.lumalyte.lg.interaction.commands.GuildCommand =
    net.lumalyte.lg.interaction.commands.GuildCommand(
        dependencies = testListenerGuildCommandDependencies(),
    )


internal fun testListenerGuildCommandDependencies(): net.lumalyte.lg.interaction.commands.GuildCommandDependencies =
    net.lumalyte.lg.interaction.commands.GuildCommandDependencies(
        lang = lazy { org.koin.core.context.GlobalContext.get().get<net.badgersmc.nexus.i18n.LangService>() },
        guildService = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.application.services.GuildService>() },
        lfgService = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.application.services.LfgService>() },
        guildRepository = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.application.persistence.GuildRepository>() },
        rankService = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.application.services.RankService>() },
        chatRankSettings = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.application.services.GuildChatRankSettingsService>() },
        memberService = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.application.services.MemberService>() },
        inviteBlocks = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.application.services.InviteBlockService>() },
        vaultService = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.application.services.GuildVaultService>() },
        warService = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.application.services.WarService>() },
        configService = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.application.services.ConfigService>() },
        menuFactory = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.interaction.menus.MenuFactory>() },
        progressionService = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.application.services.ProgressionService>() },
        historyRepository = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.application.persistence.MembershipHistoryRepository>() },
        guildChatListener = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.interaction.listeners.GuildChatListener>() },
        adminOverrideService = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.application.services.AdminOverrideService>() },
        teleportationService = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.infrastructure.services.TeleportationService>() },
        bannermanListeners = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.infrastructure.bukkit.bannerman.BannermanListeners>() },
        strikeService = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.application.services.StrikeService>() },
        bankService = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.application.services.BankService>() },
        guildCostService = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.application.services.GuildCostService>() },
        guildHomeActivationService = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.application.services.GuildHomeActivationService>() },
        warBannerService = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.infrastructure.services.WarBannerServiceBukkit>() },
        guildLoginNotificationService = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.application.services.GuildLoginNotificationService>() },
        relationService = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.application.services.RelationService>() },
        nexoEmojiService = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.infrastructure.services.NexoEmojiService>() },
        guildRecruitmentService = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.application.services.GuildRecruitmentService>() },
        guildOnboardingRepository = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.application.persistence.GuildOnboardingRepository>() },
    )


internal fun testListenerLumaGuildsCommandDependencies(): net.lumalyte.lg.interaction.commands.LumaGuildsCommandDependencies =
    net.lumalyte.lg.interaction.commands.LumaGuildsCommandDependencies(
        lang = lazy { org.koin.core.context.GlobalContext.get().get<net.badgersmc.nexus.i18n.LangService>() },
        guildService = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.application.services.GuildService>() },
        adminOverrideService = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.application.services.AdminOverrideService>() },
        storage = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.infrastructure.persistence.storage.Storage<co.aikar.idb.Database>>() },
        spawnBannerService = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.infrastructure.services.SpawnBannerServiceBukkit>() },
        warService = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.application.services.WarService>() },
        progressionService = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.application.services.ProgressionService>() },
        memberService = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.application.services.MemberService>() },
        menuFactory = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.interaction.menus.MenuFactory>() },
        progressionConfigService = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.infrastructure.services.ProgressionConfigService>() },
        guildEmojiGrantService = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.infrastructure.services.GuildEmojiGrantService>() },
        guildRolePermissionResolver = { org.koin.core.context.GlobalContext.getOrNull()?.getOrNull<net.lumalyte.lg.application.services.GuildRolePermissionResolver>() },
        progressionEventListener = { org.koin.core.context.GlobalContext.getOrNull()?.getOrNull<net.lumalyte.lg.infrastructure.listeners.ProgressionEventListener>() },
    )


internal fun testListenerClaimPvpProtectionListener(): net.lumalyte.lg.interaction.listeners.ClaimPvpProtectionListener =
    net.lumalyte.lg.interaction.listeners.ClaimPvpProtectionListener(
        combatService = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.application.services.CombatService>() },
        getClaimAtPosition = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.application.actions.claim.GetClaimAtPosition>() },
        guildService = lazy { org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.application.services.GuildService>() },
    )

