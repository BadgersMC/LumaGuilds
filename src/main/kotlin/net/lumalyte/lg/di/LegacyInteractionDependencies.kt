package net.lumalyte.lg.di

import org.koin.core.scope.Scope

/** Explicit, lazy interaction composition; no feature resolves a global container. */
internal fun Scope.legacyCombatAntiGriefListener(): net.lumalyte.lg.infrastructure.listeners.CombatAntiGriefListener =
    net.lumalyte.lg.infrastructure.listeners.CombatAntiGriefListener(
        configService = lazy { get<net.lumalyte.lg.application.services.ConfigService>() },
        warService = lazy { get<net.lumalyte.lg.application.services.WarService>() },
        memberService = lazy { get<net.lumalyte.lg.application.services.MemberService>() },
    )

internal fun Scope.legacyPlayerSessionListener(): net.lumalyte.lg.infrastructure.listeners.PlayerSessionListener =
    net.lumalyte.lg.infrastructure.listeners.PlayerSessionListener(
        dependencies = legacyPlayerSessionListenerDependencies(),
    )

internal fun Scope.legacyPlayerSessionListenerDependencies(): net.lumalyte.lg.infrastructure.listeners.PlayerSessionListenerDependencies =
    net.lumalyte.lg.infrastructure.listeners.PlayerSessionListenerDependencies(
        teleportationService = lazy { get<net.lumalyte.lg.infrastructure.services.TeleportationService>() },
        hologramService = lazy { get<net.lumalyte.lg.infrastructure.services.VaultHologramService>() },
        vaultInventoryManager = lazy { get<net.lumalyte.lg.infrastructure.vault.VaultInventoryManager>() },
        guildChatListener = lazy { get<net.lumalyte.lg.interaction.listeners.GuildChatListener>() },
        warNotificationService = lazy { get<net.lumalyte.lg.application.services.WarNotificationService>() },
        questCompletionNotifier = lazy { get<net.lumalyte.lg.application.services.QuestCompletionNotifier>() },
        guildLoginNotificationService = lazy { get<net.lumalyte.lg.application.services.GuildLoginNotificationService>() },
        questAvailabilityNotifier = lazy { get<net.lumalyte.lg.infrastructure.services.QuestAvailabilityNotifierBukkit>() },
    )

internal fun Scope.legacyVaultProtectionListener(): net.lumalyte.lg.infrastructure.listeners.VaultProtectionListener =
    net.lumalyte.lg.infrastructure.listeners.VaultProtectionListener(
        dependencies = legacyVaultProtectionListenerDependencies(),
    )

internal fun Scope.legacyVaultProtectionListenerDependencies(): net.lumalyte.lg.infrastructure.listeners.VaultProtectionListenerDependencies =
    net.lumalyte.lg.infrastructure.listeners.VaultProtectionListenerDependencies(
        vaultService = lazy { get<net.lumalyte.lg.application.services.GuildVaultService>() },
        guildService = lazy { get<net.lumalyte.lg.application.services.GuildService>() },
        guildRepository = lazy { get<net.lumalyte.lg.application.persistence.GuildRepository>() },
        rankService = lazy { get<net.lumalyte.lg.application.services.RankService>() },
        memberService = lazy { get<net.lumalyte.lg.application.services.MemberService>() },
        configService = lazy { get<net.lumalyte.lg.application.services.ConfigService>() },
        hologramService = lazy { get<net.lumalyte.lg.infrastructure.services.VaultHologramService>() },
        lang = lazy { get<net.badgersmc.nexus.i18n.LangService>() },
    )

internal fun Scope.legacyWarKillTrackingListener(): net.lumalyte.lg.infrastructure.listeners.WarKillTrackingListener =
    net.lumalyte.lg.infrastructure.listeners.WarKillTrackingListener(
        warService = lazy { get<net.lumalyte.lg.application.services.WarService>() },
        memberService = lazy { get<net.lumalyte.lg.application.services.MemberService>() },
        lang = lazy { get<net.badgersmc.nexus.i18n.LangService>() },
    )

internal fun Scope.legacyLumaGuildsExpansion(): net.lumalyte.lg.infrastructure.placeholders.LumaGuildsExpansion =
    net.lumalyte.lg.infrastructure.placeholders.LumaGuildsExpansion(
        dependencies = legacyLumaGuildsExpansionDependencies(),
    )

internal fun Scope.legacyLumaGuildsExpansionDependencies(): net.lumalyte.lg.infrastructure.placeholders.LumaGuildsExpansionDependencies =
    net.lumalyte.lg.infrastructure.placeholders.LumaGuildsExpansionDependencies(
        guildService = lazy { get<net.lumalyte.lg.application.services.GuildService>() },
        memberService = lazy { get<net.lumalyte.lg.application.services.MemberService>() },
        bankService = lazy { get<net.lumalyte.lg.application.services.BankService>() },
        killService = lazy { get<net.lumalyte.lg.application.services.KillService>() },
        rankService = lazy { get<net.lumalyte.lg.application.services.RankService>() },
        warService = lazy { get<net.lumalyte.lg.application.services.WarService>() },
        relationService = lazy { get<net.lumalyte.lg.application.services.RelationService>() },
        progressionRepository = lazy { get<net.lumalyte.lg.application.persistence.ProgressionRepository>() },
        progressionService = lazy { get<net.lumalyte.lg.application.services.ProgressionService>() },
        configService = lazy { get<net.lumalyte.lg.application.services.ConfigService>() },
        leaderboardRepository = lazy { get<net.lumalyte.lg.application.persistence.LeaderboardRepository>() },
        nexoEmojiService = lazy { get<net.lumalyte.lg.infrastructure.services.NexoEmojiService>() },
        questService = lazy { get<net.lumalyte.lg.application.services.QuestService>() },
        storage = lazy { get<net.lumalyte.lg.infrastructure.persistence.storage.Storage<co.aikar.idb.Database>>() },
        seasonalElo = lazy { get<net.lumalyte.lg.infrastructure.services.SeasonalEloCoordinator>() },
    )

internal fun Scope.legacyLumaGuildsChannelDependencies(): net.lumalyte.lg.infrastructure.services.LumaGuildsChannelDependencies =
    net.lumalyte.lg.infrastructure.services.LumaGuildsChannelDependencies(
        memberService = lazy { get<net.lumalyte.lg.application.services.MemberService>() },
        guildService = lazy { get<net.lumalyte.lg.application.services.GuildService>() },
        relationService = lazy { get<net.lumalyte.lg.application.services.RelationService>() },
        chatSettingsRepository = lazy { get<net.lumalyte.lg.application.persistence.ChatSettingsRepository>() },
        rankSettings = lazy { get<net.lumalyte.lg.application.services.GuildChatRankSettingsService>() },
        rankService = lazy { get<net.lumalyte.lg.application.services.RankService>() },
    )

internal fun Scope.legacyAddFlagCommand(): net.lumalyte.lg.interaction.commands.AddFlagCommand =
    net.lumalyte.lg.interaction.commands.AddFlagCommand(
        claimDependencies = legacyClaimCommandDependencies(),
        enableClaimFlag = lazy { get<net.lumalyte.lg.application.actions.claim.flag.EnableClaimFlag>() },
    )

internal fun Scope.legacyBedrockCacheStatsCommand(): net.lumalyte.lg.interaction.commands.BedrockCacheStatsCommand =
    net.lumalyte.lg.interaction.commands.BedrockCacheStatsCommand(
        lang = lazy { get<net.badgersmc.nexus.i18n.LangService>() },
        formCacheService = lazy { get<net.lumalyte.lg.application.services.FormCacheService>() },
    )

internal fun Scope.legacyClaimCommand(): net.lumalyte.lg.interaction.commands.ClaimCommand =
    net.lumalyte.lg.interaction.commands.ClaimCommand(
        claimDependencies = legacyClaimCommandDependencies(),
    )

internal fun Scope.legacyClaimCommandDependencies(): net.lumalyte.lg.interaction.commands.ClaimCommandDependencies =
    net.lumalyte.lg.interaction.commands.ClaimCommandDependencies(
        lang = lazy { get<net.badgersmc.nexus.i18n.LangService>() },
        getPartitionByPosition = lazy { get<net.lumalyte.lg.application.actions.claim.partition.GetPartitionByPosition>() },
        doesPlayerHaveClaimOverride = lazy { get<net.lumalyte.lg.application.actions.player.DoesPlayerHaveClaimOverride>() },
        getClaimDetails = lazy { get<net.lumalyte.lg.application.actions.claim.metadata.GetClaimDetails>() },
        givePlayerClaimTool = lazy { get<net.lumalyte.lg.application.actions.player.tool.GivePlayerClaimTool>() },
        claimManagementAuthorizer = lazy { get<net.lumalyte.lg.application.services.ClaimManagementAuthorizer>() },
    )

internal fun Scope.legacyClaimListCommand(): net.lumalyte.lg.interaction.commands.ClaimListCommand =
    net.lumalyte.lg.interaction.commands.ClaimListCommand(
        lang = lazy { get<net.badgersmc.nexus.i18n.LangService>() },
        listPlayerClaims = lazy { get<net.lumalyte.lg.application.actions.claim.ListPlayerClaims>() },
        getClaimBlockCount = lazy { get<net.lumalyte.lg.application.actions.claim.metadata.GetClaimBlockCount>() },
    )

internal fun Scope.legacyClaimMenuCommand(): net.lumalyte.lg.interaction.commands.ClaimMenuCommand =
    net.lumalyte.lg.interaction.commands.ClaimMenuCommand(
        menuFactory = lazy { get<net.lumalyte.lg.interaction.menus.MenuFactory>() },
    )

internal fun Scope.legacyClaimOverrideCommand(): net.lumalyte.lg.interaction.commands.ClaimOverrideCommand =
    net.lumalyte.lg.interaction.commands.ClaimOverrideCommand(
        lang = lazy { get<net.badgersmc.nexus.i18n.LangService>() },
        toggleClaimOverride = lazy { get<net.lumalyte.lg.application.actions.player.ToggleClaimOverride>() },
    )

internal fun Scope.legacyDescriptionCommand(): net.lumalyte.lg.interaction.commands.DescriptionCommand =
    net.lumalyte.lg.interaction.commands.DescriptionCommand(
        claimDependencies = legacyClaimCommandDependencies(),
        updateClaimDescription = lazy { get<net.lumalyte.lg.application.actions.claim.metadata.UpdateClaimDescription>() },
    )

internal fun Scope.legacyFullscreenAnnounceCommand(): net.lumalyte.lg.interaction.commands.FullscreenAnnounceCommand =
    net.lumalyte.lg.interaction.commands.FullscreenAnnounceCommand(
        guilds = lazy { get<net.lumalyte.lg.application.services.GuildService>() },
        members = lazy { get<net.lumalyte.lg.application.services.MemberService>() },
        service = lazy { get<net.lumalyte.lg.application.services.ChatService>() },
        penalties = lazy { get<net.lumalyte.lg.application.services.PenaltyService>() },
        lang = lazy { get<net.badgersmc.nexus.i18n.LangService>() },
    )

internal fun Scope.legacyGuildCommand(): net.lumalyte.lg.interaction.commands.GuildCommand =
    net.lumalyte.lg.interaction.commands.GuildCommand(
        dependencies = legacyGuildCommandDependencies(),
    )

internal fun Scope.legacyGuildCommandDependencies(): net.lumalyte.lg.interaction.commands.GuildCommandDependencies =
    net.lumalyte.lg.interaction.commands.GuildCommandDependencies(
        lang = lazy { get<net.badgersmc.nexus.i18n.LangService>() },
        guildService = lazy { get<net.lumalyte.lg.application.services.GuildService>() },
        lfgService = lazy { get<net.lumalyte.lg.application.services.LfgService>() },
        guildRepository = lazy { get<net.lumalyte.lg.application.persistence.GuildRepository>() },
        rankService = lazy { get<net.lumalyte.lg.application.services.RankService>() },
        chatRankSettings = lazy { get<net.lumalyte.lg.application.services.GuildChatRankSettingsService>() },
        memberService = lazy { get<net.lumalyte.lg.application.services.MemberService>() },
        inviteBlocks = lazy { get<net.lumalyte.lg.application.services.InviteBlockService>() },
        vaultService = lazy { get<net.lumalyte.lg.application.services.GuildVaultService>() },
        warService = lazy { get<net.lumalyte.lg.application.services.WarService>() },
        configService = lazy { get<net.lumalyte.lg.application.services.ConfigService>() },
        menuFactory = lazy { get<net.lumalyte.lg.interaction.menus.MenuFactory>() },
        progressionService = lazy { get<net.lumalyte.lg.application.services.ProgressionService>() },
        historyRepository = lazy { get<net.lumalyte.lg.application.persistence.MembershipHistoryRepository>() },
        guildChatListener = lazy { get<net.lumalyte.lg.interaction.listeners.GuildChatListener>() },
        adminOverrideService = lazy { get<net.lumalyte.lg.application.services.AdminOverrideService>() },
        teleportationService = lazy { get<net.lumalyte.lg.infrastructure.services.TeleportationService>() },
        bannermanListeners = lazy { get<net.lumalyte.lg.infrastructure.bukkit.bannerman.BannermanListeners>() },
        strikeService = lazy { get<net.lumalyte.lg.application.services.StrikeService>() },
        bankService = lazy { get<net.lumalyte.lg.application.services.BankService>() },
        guildCostService = lazy { get<net.lumalyte.lg.application.services.GuildCostService>() },
        guildHomeActivationService = lazy { get<net.lumalyte.lg.application.services.GuildHomeActivationService>() },
        warBannerService = lazy { get<net.lumalyte.lg.infrastructure.services.WarBannerServiceBukkit>() },
        guildLoginNotificationService = lazy { get<net.lumalyte.lg.application.services.GuildLoginNotificationService>() },
        relationService = lazy { get<net.lumalyte.lg.application.services.RelationService>() },
        nexoEmojiService = lazy { get<net.lumalyte.lg.infrastructure.services.NexoEmojiService>() },
        guildRecruitmentService = lazy { get<net.lumalyte.lg.application.services.GuildRecruitmentService>() },
        guildOnboardingRepository = lazy { get<net.lumalyte.lg.application.persistence.GuildOnboardingRepository>() },
    )

internal fun Scope.legacyInfoCommand(): net.lumalyte.lg.interaction.commands.InfoCommand =
    net.lumalyte.lg.interaction.commands.InfoCommand(
        claimDependencies = legacyClaimCommandDependencies(),
        getClaimFlags = lazy { get<net.lumalyte.lg.application.actions.claim.flag.GetClaimFlags>() },
        getClaimPermissions = lazy { get<net.lumalyte.lg.application.actions.claim.permission.GetClaimPermissions>() },
        getClaimPartitions = lazy { get<net.lumalyte.lg.application.actions.claim.partition.GetClaimPartitions>() },
        getClaimBlockCount = lazy { get<net.lumalyte.lg.application.actions.claim.metadata.GetClaimBlockCount>() },
        getPlayersWithPermissionInClaim = lazy { get<net.lumalyte.lg.application.actions.claim.permission.GetPlayersWithPermissionInClaim>() },
    )

internal fun Scope.legacyLumaGuildsCommand(): net.lumalyte.lg.interaction.commands.LumaGuildsCommand =
    net.lumalyte.lg.interaction.commands.LumaGuildsCommand(
        dependencies = legacyLumaGuildsCommandDependencies(),
    )

internal fun Scope.legacyLumaGuildsCommandDependencies(): net.lumalyte.lg.interaction.commands.LumaGuildsCommandDependencies =
    net.lumalyte.lg.interaction.commands.LumaGuildsCommandDependencies(
        lang = lazy { get<net.badgersmc.nexus.i18n.LangService>() },
        guildService = lazy { get<net.lumalyte.lg.application.services.GuildService>() },
        adminOverrideService = lazy { get<net.lumalyte.lg.application.services.AdminOverrideService>() },
        storage = lazy { get<net.lumalyte.lg.infrastructure.persistence.storage.Storage<co.aikar.idb.Database>>() },
        spawnBannerService = lazy { get<net.lumalyte.lg.infrastructure.services.SpawnBannerServiceBukkit>() },
        warService = lazy { get<net.lumalyte.lg.application.services.WarService>() },
        progressionService = lazy { get<net.lumalyte.lg.application.services.ProgressionService>() },
        memberService = lazy { get<net.lumalyte.lg.application.services.MemberService>() },
        menuFactory = lazy { get<net.lumalyte.lg.interaction.menus.MenuFactory>() },
        progressionConfigService = lazy { get<net.lumalyte.lg.infrastructure.services.ProgressionConfigService>() },
        guildEmojiGrantService = lazy { get<net.lumalyte.lg.infrastructure.services.GuildEmojiGrantService>() },
        guildRolePermissionResolver = { getOrNull<net.lumalyte.lg.application.services.GuildRolePermissionResolver>() },
        progressionEventListener = { getOrNull<net.lumalyte.lg.infrastructure.listeners.ProgressionEventListener>() },
    )

internal fun Scope.legacyPartitionsCommand(): net.lumalyte.lg.interaction.commands.PartitionsCommand =
    net.lumalyte.lg.interaction.commands.PartitionsCommand(
        claimDependencies = legacyClaimCommandDependencies(),
        getClaimPartitions = lazy { get<net.lumalyte.lg.application.actions.claim.partition.GetClaimPartitions>() },
    )

internal fun Scope.legacyPartyChatCommand(): net.lumalyte.lg.interaction.commands.PartyChatCommand =
    net.lumalyte.lg.interaction.commands.PartyChatCommand(
        dependencies = legacyPartyChatCommandDependencies(),
    )

internal fun Scope.legacyPartyChatCommandDependencies(): net.lumalyte.lg.interaction.commands.PartyChatCommandDependencies =
    net.lumalyte.lg.interaction.commands.PartyChatCommandDependencies(
        lang = lazy { get<net.badgersmc.nexus.i18n.LangService>() },
        chatService = lazy { get<net.lumalyte.lg.application.services.ChatService>() },
        partyService = lazy { get<net.lumalyte.lg.application.services.PartyService>() },
        guildService = lazy { get<net.lumalyte.lg.application.services.GuildService>() },
        memberService = lazy { get<net.lumalyte.lg.application.services.MemberService>() },
        partyRepository = lazy { get<net.lumalyte.lg.application.persistence.PartyRepository>() },
        preferenceRepository = lazy { get<net.lumalyte.lg.application.persistence.PlayerPartyPreferenceRepository>() },
        configService = lazy { get<net.lumalyte.lg.application.services.ConfigService>() },
    )

internal fun Scope.legacyQuickAllyChatCommand(): net.lumalyte.lg.interaction.commands.QuickAllyChatCommand =
    net.lumalyte.lg.interaction.commands.QuickAllyChatCommand(
        lang = lazy { get<net.badgersmc.nexus.i18n.LangService>() },
        guildService = lazy { get<net.lumalyte.lg.application.services.GuildService>() },
    )

internal fun Scope.legacyQuickAnnounceCommand(): net.lumalyte.lg.interaction.commands.QuickAnnounceCommand =
    net.lumalyte.lg.interaction.commands.QuickAnnounceCommand(
        lang = lazy { get<net.badgersmc.nexus.i18n.LangService>() },
        chatService = lazy { get<net.lumalyte.lg.application.services.ChatService>() },
        guildService = lazy { get<net.lumalyte.lg.application.services.GuildService>() },
        memberService = lazy { get<net.lumalyte.lg.application.services.MemberService>() },
    )

internal fun Scope.legacyQuickGuildChatCommand(): net.lumalyte.lg.interaction.commands.QuickGuildChatCommand =
    net.lumalyte.lg.interaction.commands.QuickGuildChatCommand(
        lang = lazy { get<net.badgersmc.nexus.i18n.LangService>() },
        guildService = lazy { get<net.lumalyte.lg.application.services.GuildService>() },
        penaltyService = lazy { get<net.lumalyte.lg.application.services.PenaltyService>() },
    )

internal fun Scope.legacyQuickModChatCommand(): net.lumalyte.lg.interaction.commands.QuickModChatCommand =
    net.lumalyte.lg.interaction.commands.QuickModChatCommand(
        lang = lazy { get<net.badgersmc.nexus.i18n.LangService>() },
        guildService = lazy { get<net.lumalyte.lg.application.services.GuildService>() },
        memberService = lazy { get<net.lumalyte.lg.application.services.MemberService>() },
    )

internal fun Scope.legacyRemoveCommand(): net.lumalyte.lg.interaction.commands.RemoveCommand =
    net.lumalyte.lg.interaction.commands.RemoveCommand(
        claimDependencies = legacyClaimCommandDependencies(),
        removePartition = lazy { get<net.lumalyte.lg.application.actions.claim.partition.RemovePartition>() },
    )

internal fun Scope.legacyRemoveFlagCommand(): net.lumalyte.lg.interaction.commands.RemoveFlagCommand =
    net.lumalyte.lg.interaction.commands.RemoveFlagCommand(
        claimDependencies = legacyClaimCommandDependencies(),
        disableClaimFlag = lazy { get<net.lumalyte.lg.application.actions.claim.flag.DisableClaimFlag>() },
    )

internal fun Scope.legacyRenameCommand(): net.lumalyte.lg.interaction.commands.RenameCommand =
    net.lumalyte.lg.interaction.commands.RenameCommand(
        claimDependencies = legacyClaimCommandDependencies(),
        updateClaimName = lazy { get<net.lumalyte.lg.application.actions.claim.metadata.UpdateClaimName>() },
    )

internal fun Scope.legacyTrustAllCommand(): net.lumalyte.lg.interaction.commands.TrustAllCommand =
    net.lumalyte.lg.interaction.commands.TrustAllCommand(
        claimDependencies = legacyClaimCommandDependencies(),
        grantClaimWidePermission = lazy { get<net.lumalyte.lg.application.actions.claim.permission.GrantClaimWidePermission>() },
    )

internal fun Scope.legacyTrustCommand(): net.lumalyte.lg.interaction.commands.TrustCommand =
    net.lumalyte.lg.interaction.commands.TrustCommand(
        claimDependencies = legacyClaimCommandDependencies(),
        grantPlayerClaimPermission = lazy { get<net.lumalyte.lg.application.actions.claim.permission.GrantPlayerClaimPermission>() },
    )

internal fun Scope.legacyTrustListCommand(): net.lumalyte.lg.interaction.commands.TrustListCommand =
    net.lumalyte.lg.interaction.commands.TrustListCommand(
        claimDependencies = legacyClaimCommandDependencies(),
        getPlayersWithPermissionInClaim = lazy { get<net.lumalyte.lg.application.actions.claim.permission.GetPlayersWithPermissionInClaim>() },
        getClaimPlayerPermissions = lazy { get<net.lumalyte.lg.application.actions.claim.permission.GetClaimPlayerPermissions>() },
    )

internal fun Scope.legacyUntrustAllCommand(): net.lumalyte.lg.interaction.commands.UntrustAllCommand =
    net.lumalyte.lg.interaction.commands.UntrustAllCommand(
        claimDependencies = legacyClaimCommandDependencies(),
        revokeClaimWidePermission = lazy { get<net.lumalyte.lg.application.actions.claim.permission.RevokeClaimWidePermission>() },
    )

internal fun Scope.legacyUntrustCommand(): net.lumalyte.lg.interaction.commands.UntrustCommand =
    net.lumalyte.lg.interaction.commands.UntrustCommand(
        claimDependencies = legacyClaimCommandDependencies(),
        revokePlayerClaimPermission = lazy { get<net.lumalyte.lg.application.actions.claim.permission.RevokePlayerClaimPermission>() },
    )

internal fun Scope.legacyBannerSelectionListener(): net.lumalyte.lg.interaction.listeners.BannerSelectionListener =
    net.lumalyte.lg.interaction.listeners.BannerSelectionListener(
        guildService = lazy { get<net.lumalyte.lg.application.services.GuildService>() },
        lang = lazy { get<net.badgersmc.nexus.i18n.LangService>() },
    )

internal fun Scope.legacyClaimAnchorListener(): net.lumalyte.lg.interaction.listeners.ClaimAnchorListener =
    net.lumalyte.lg.interaction.listeners.ClaimAnchorListener(
        dependencies = legacyClaimAnchorListenerDependencies(),
    )

internal fun Scope.legacyClaimAnchorListenerDependencies(): net.lumalyte.lg.interaction.listeners.ClaimAnchorListenerDependencies =
    net.lumalyte.lg.interaction.listeners.ClaimAnchorListenerDependencies(
        lang = lazy { get<net.badgersmc.nexus.i18n.LangService>() },
        getClaimAnchorAtPosition = lazy { get<net.lumalyte.lg.application.actions.claim.anchor.GetClaimAnchorAtPosition>() },
        createClaim = lazy { get<net.lumalyte.lg.application.actions.claim.CreateClaim>() },
        doesPlayerHaveTransferRequest = lazy { get<net.lumalyte.lg.application.actions.claim.transfer.DoesPlayerHaveTransferRequest>() },
        doesPlayerHaveClaimOverride = lazy { get<net.lumalyte.lg.application.actions.player.DoesPlayerHaveClaimOverride>() },
        claimManagementAuthorizer = lazy { get<net.lumalyte.lg.application.services.ClaimManagementAuthorizer>() },
        menuFactory = lazy { get<net.lumalyte.lg.interaction.menus.MenuFactory>() },
    )

internal fun Scope.legacyClaimDestructionListener(): net.lumalyte.lg.interaction.listeners.ClaimDestructionListener =
    net.lumalyte.lg.interaction.listeners.ClaimDestructionListener(
        lang = lazy { get<net.badgersmc.nexus.i18n.LangService>() },
        getClaimAnchorAtPosition = lazy { get<net.lumalyte.lg.application.actions.claim.anchor.GetClaimAnchorAtPosition>() },
        breakClaimAnchor = lazy { get<net.lumalyte.lg.application.actions.claim.anchor.BreakClaimAnchor>() },
        doesPlayerHaveClaimOverride = lazy { get<net.lumalyte.lg.application.actions.player.DoesPlayerHaveClaimOverride>() },
        claimManagementAuthorizer = lazy { get<net.lumalyte.lg.application.services.ClaimManagementAuthorizer>() },
    )

internal fun Scope.legacyClaimPvpProtectionListener(): net.lumalyte.lg.interaction.listeners.ClaimPvpProtectionListener =
    net.lumalyte.lg.interaction.listeners.ClaimPvpProtectionListener(
        combatService = lazy { get<net.lumalyte.lg.application.services.CombatService>() },
        getClaimAtPosition = lazy { get<net.lumalyte.lg.application.actions.claim.GetClaimAtPosition>() },
        guildService = lazy { get<net.lumalyte.lg.application.services.GuildService>() },
    )

internal fun Scope.legacyCloseInventoryListener(): net.lumalyte.lg.interaction.listeners.CloseInventoryListener =
    net.lumalyte.lg.interaction.listeners.CloseInventoryListener(
        unregisterClaimMenuOpening = lazy { get<net.lumalyte.lg.application.actions.player.UnregisterClaimMenuOpening>() },
    )

internal fun Scope.legacyEditToolListener(): net.lumalyte.lg.interaction.listeners.EditToolListener =
    net.lumalyte.lg.interaction.listeners.EditToolListener(
        dependencies = legacyEditToolListenerDependencies(),
    )

internal fun Scope.legacyEditToolListenerDependencies(): net.lumalyte.lg.interaction.listeners.EditToolListenerDependencies =
    net.lumalyte.lg.interaction.listeners.EditToolListenerDependencies(
        lang = lazy { get<net.badgersmc.nexus.i18n.LangService>() },
        getPartitionByPosition = lazy { get<net.lumalyte.lg.application.actions.claim.partition.GetPartitionByPosition>() },
        displayVisualisation = lazy { get<net.lumalyte.lg.application.actions.player.visualisation.DisplayVisualisation>() },
        createPartition = lazy { get<net.lumalyte.lg.application.actions.claim.partition.CreatePartition>() },
        getRemainingClaimBlockCount = lazy { get<net.lumalyte.lg.application.actions.player.GetRemainingClaimBlockCount>() },
        getClaimAtPosition = lazy { get<net.lumalyte.lg.application.actions.claim.GetClaimAtPosition>() },
        doesPlayerHaveClaimOverride = lazy { get<net.lumalyte.lg.application.actions.player.DoesPlayerHaveClaimOverride>() },
        resizePartition = lazy { get<net.lumalyte.lg.application.actions.claim.partition.ResizePartition>() },
        isItemClaimTool = lazy { get<net.lumalyte.lg.application.actions.player.tool.IsItemClaimTool>() },
        coroutineScope = lazy { get<kotlinx.coroutines.CoroutineScope>() },
        menuFactory = lazy { get<net.lumalyte.lg.interaction.menus.MenuFactory>() },
        schedulerService = lazy { get<net.lumalyte.lg.application.services.scheduling.SchedulerService>() },
        displaySelectionVisualisation = lazy { get<net.lumalyte.lg.application.actions.player.visualisation.DisplaySelectionVisualisation>() },
        clearSelectionVisualisation = lazy { get<net.lumalyte.lg.application.actions.player.visualisation.ClearSelectionVisualisation>() },
        refreshVisualisation = lazy { get<net.lumalyte.lg.application.actions.player.visualisation.RefreshVisualisation>() },
        claimManagementAuthorizer = lazy { get<net.lumalyte.lg.application.services.ClaimManagementAuthorizer>() },
    )

internal fun Scope.legacyHarvestReplantListener(): net.lumalyte.lg.interaction.listeners.HarvestReplantListener =
    net.lumalyte.lg.interaction.listeners.HarvestReplantListener(
        config = lazy { get<net.lumalyte.lg.config.MainConfig>() },
    )

internal fun Scope.legacyMoveToolListener(): net.lumalyte.lg.interaction.listeners.MoveToolListener =
    net.lumalyte.lg.interaction.listeners.MoveToolListener(
        lang = lazy { get<net.badgersmc.nexus.i18n.LangService>() },
        moveClaimAnchor = lazy { get<net.lumalyte.lg.application.actions.claim.anchor.MoveClaimAnchor>() },
        getClaimIdFromMoveTool = lazy { get<net.lumalyte.lg.application.actions.player.tool.GetClaimIdFromMoveTool>() },
    )

internal fun Scope.legacyPartitionUpdateListener(): net.lumalyte.lg.interaction.listeners.PartitionUpdateListener =
    net.lumalyte.lg.interaction.listeners.PartitionUpdateListener(
        getClaimDetails = lazy { get<net.lumalyte.lg.application.actions.claim.metadata.GetClaimDetails>() },
        getClaimPartitions = lazy { get<net.lumalyte.lg.application.actions.claim.partition.GetClaimPartitions>() },
        isPlayerVisualising = lazy { get<net.lumalyte.lg.application.actions.player.visualisation.IsPlayerVisualising>() },
        refreshVisualisation = lazy { get<net.lumalyte.lg.application.actions.player.visualisation.RefreshVisualisation>() },
    )

internal fun Scope.legacyPartyChatListener(): net.lumalyte.lg.interaction.listeners.PartyChatListener =
    net.lumalyte.lg.interaction.listeners.PartyChatListener(
        dependencies = legacyPartyChatListenerDependencies(),
    )

internal fun Scope.legacyPartyChatListenerDependencies(): net.lumalyte.lg.interaction.listeners.PartyChatListenerDependencies =
    net.lumalyte.lg.interaction.listeners.PartyChatListenerDependencies(
        chatService = lazy { get<net.lumalyte.lg.application.services.ChatService>() },
        preferenceRepository = lazy { get<net.lumalyte.lg.application.persistence.PlayerPartyPreferenceRepository>() },
        partyRepository = lazy { get<net.lumalyte.lg.application.persistence.PartyRepository>() },
        guildService = lazy { get<net.lumalyte.lg.application.services.GuildService>() },
        memberService = lazy { get<net.lumalyte.lg.application.services.MemberService>() },
        configService = lazy { get<net.lumalyte.lg.application.services.ConfigService>() },
        chatInputListener = lazy { get<net.lumalyte.lg.interaction.listeners.ChatInputListener>() },
        plugin = lazy { get<org.bukkit.plugin.Plugin>() },
        lang = lazy { get<net.badgersmc.nexus.i18n.LangService>() },
    )

internal fun Scope.legacyPlayerClaimProtectionListener(): net.lumalyte.lg.interaction.listeners.PlayerClaimProtectionListener =
    net.lumalyte.lg.interaction.listeners.PlayerClaimProtectionListener(
        lang = lazy { get<net.badgersmc.nexus.i18n.LangService>() },
        isPlayerActionAllowed = lazy { get<net.lumalyte.lg.application.actions.claim.IsPlayerActionAllowed>() },
    )

internal fun Scope.legacyToolRemovalListener(): net.lumalyte.lg.interaction.listeners.ToolRemovalListener =
    net.lumalyte.lg.interaction.listeners.ToolRemovalListener(
        isItemClaimTool = lazy { get<net.lumalyte.lg.application.actions.player.tool.IsItemClaimTool>() },
        isItemMoveTool = lazy { get<net.lumalyte.lg.application.actions.player.tool.IsItemMoveTool>() },
    )

internal fun Scope.legacyWorldClaimProtectionListener(): net.lumalyte.lg.interaction.listeners.WorldClaimProtectionListener =
    net.lumalyte.lg.interaction.listeners.WorldClaimProtectionListener(
        isWorldActionAllowed = lazy { get<net.lumalyte.lg.application.actions.claim.IsWorldActionAllowed>() },
        getClaimAtPosition = lazy { get<net.lumalyte.lg.application.actions.claim.GetClaimAtPosition>() },
    )
