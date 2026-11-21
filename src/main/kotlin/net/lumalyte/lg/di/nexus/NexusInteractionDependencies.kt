package net.lumalyte.lg.di.nexus

import net.badgersmc.nexus.core.NexusContext

/** Explicit, lazy interaction composition; no feature resolves a global container. */
internal fun NexusContext.nexusCombatAntiGriefListener(): net.lumalyte.lg.infrastructure.listeners.CombatAntiGriefListener =
    net.lumalyte.lg.infrastructure.listeners.CombatAntiGriefListener(
        configService = lazy { getBean<net.lumalyte.lg.application.services.ConfigService>() },
        warService = lazy { getBean<net.lumalyte.lg.application.services.WarService>() },
        memberService = lazy { getBean<net.lumalyte.lg.application.services.MemberService>() },
    )

internal fun NexusContext.nexusPlayerSessionListener(): net.lumalyte.lg.infrastructure.listeners.PlayerSessionListener =
    net.lumalyte.lg.infrastructure.listeners.PlayerSessionListener(
        dependencies = nexusPlayerSessionListenerDependencies(),
    )

internal fun NexusContext.nexusPlayerSessionListenerDependencies(): net.lumalyte.lg.infrastructure.listeners.PlayerSessionListenerDependencies =
    net.lumalyte.lg.infrastructure.listeners.PlayerSessionListenerDependencies(
        teleportationService = lazy { getBean<net.lumalyte.lg.infrastructure.services.TeleportationService>() },
        hologramService = lazy { getBean<net.lumalyte.lg.infrastructure.services.VaultHologramService>() },
        vaultInventoryManager = lazy { getBean<net.lumalyte.lg.infrastructure.vault.VaultInventoryManager>() },
        guildChatListener = lazy { getBean<net.lumalyte.lg.interaction.listeners.GuildChatListener>() },
        warNotificationService = lazy { getBean<net.lumalyte.lg.application.services.WarNotificationService>() },
        questCompletionNotifier = lazy { getBean<net.lumalyte.lg.application.services.QuestCompletionNotifier>() },
        guildLoginNotificationService = lazy { getBean<net.lumalyte.lg.application.services.GuildLoginNotificationService>() },
        questAvailabilityNotifier = lazy { getBean<net.lumalyte.lg.infrastructure.services.QuestAvailabilityNotifierBukkit>() },
    )

internal fun NexusContext.nexusVaultProtectionListener(): net.lumalyte.lg.infrastructure.listeners.VaultProtectionListener =
    net.lumalyte.lg.infrastructure.listeners.VaultProtectionListener(
        dependencies = nexusVaultProtectionListenerDependencies(),
    )

internal fun NexusContext.nexusVaultProtectionListenerDependencies(): net.lumalyte.lg.infrastructure.listeners.VaultProtectionListenerDependencies =
    net.lumalyte.lg.infrastructure.listeners.VaultProtectionListenerDependencies(
        vaultService = lazy { getBean<net.lumalyte.lg.application.services.GuildVaultService>() },
        guildService = lazy { getBean<net.lumalyte.lg.application.services.GuildService>() },
        guildRepository = lazy { getBean<net.lumalyte.lg.application.persistence.GuildRepository>() },
        rankService = lazy { getBean<net.lumalyte.lg.application.services.RankService>() },
        memberService = lazy { getBean<net.lumalyte.lg.application.services.MemberService>() },
        configService = lazy { getBean<net.lumalyte.lg.application.services.ConfigService>() },
        hologramService = lazy { getBean<net.lumalyte.lg.infrastructure.services.VaultHologramService>() },
        lang = lazy { getBean<net.badgersmc.nexus.i18n.LangService>() },
    )

internal fun NexusContext.nexusWarKillTrackingListener(): net.lumalyte.lg.infrastructure.listeners.WarKillTrackingListener =
    net.lumalyte.lg.infrastructure.listeners.WarKillTrackingListener(
        warService = lazy { getBean<net.lumalyte.lg.application.services.WarService>() },
        memberService = lazy { getBean<net.lumalyte.lg.application.services.MemberService>() },
        lang = lazy { getBean<net.badgersmc.nexus.i18n.LangService>() },
    )

internal fun NexusContext.nexusLumaGuildsExpansion(): net.lumalyte.lg.infrastructure.placeholders.LumaGuildsExpansion =
    net.lumalyte.lg.infrastructure.placeholders.LumaGuildsExpansion(
        dependencies = nexusLumaGuildsExpansionDependencies(),
    )

internal fun NexusContext.nexusLumaGuildsExpansionDependencies(): net.lumalyte.lg.infrastructure.placeholders.LumaGuildsExpansionDependencies =
    net.lumalyte.lg.infrastructure.placeholders.LumaGuildsExpansionDependencies(
        guildService = lazy { getBean<net.lumalyte.lg.application.services.GuildService>() },
        memberService = lazy { getBean<net.lumalyte.lg.application.services.MemberService>() },
        bankService = lazy { getBean<net.lumalyte.lg.application.services.BankService>() },
        killService = lazy { getBean<net.lumalyte.lg.application.services.KillService>() },
        rankService = lazy { getBean<net.lumalyte.lg.application.services.RankService>() },
        warService = lazy { getBean<net.lumalyte.lg.application.services.WarService>() },
        relationService = lazy { getBean<net.lumalyte.lg.application.services.RelationService>() },
        progressionRepository = lazy { getBean<net.lumalyte.lg.application.persistence.ProgressionRepository>() },
        progressionService = lazy { getBean<net.lumalyte.lg.application.services.ProgressionService>() },
        configService = lazy { getBean<net.lumalyte.lg.application.services.ConfigService>() },
        leaderboardRepository = lazy { getBean<net.lumalyte.lg.application.persistence.LeaderboardRepository>() },
        nexoEmojiService = lazy { getBean<net.lumalyte.lg.infrastructure.services.NexoEmojiService>() },
        questService = lazy { getBean<net.lumalyte.lg.application.services.QuestService>() },
        storage = lazy { getBean<net.lumalyte.lg.infrastructure.persistence.storage.Storage<co.aikar.idb.Database>>() },
        seasonalElo = lazy { getBean<net.lumalyte.lg.infrastructure.services.SeasonalEloCoordinator>() },
    )

internal fun NexusContext.nexusLumaGuildsChannelDependencies(): net.lumalyte.lg.infrastructure.services.LumaGuildsChannelDependencies =
    net.lumalyte.lg.infrastructure.services.LumaGuildsChannelDependencies(
        memberService = lazy { getBean<net.lumalyte.lg.application.services.MemberService>() },
        guildService = lazy { getBean<net.lumalyte.lg.application.services.GuildService>() },
        relationService = lazy { getBean<net.lumalyte.lg.application.services.RelationService>() },
        chatSettingsRepository = lazy { getBean<net.lumalyte.lg.application.persistence.ChatSettingsRepository>() },
        rankSettings = lazy { getBean<net.lumalyte.lg.application.services.GuildChatRankSettingsService>() },
        rankService = lazy { getBean<net.lumalyte.lg.application.services.RankService>() },
    )

internal fun NexusContext.nexusAddFlagCommand(): net.lumalyte.lg.interaction.commands.AddFlagCommand =
    net.lumalyte.lg.interaction.commands.AddFlagCommand(
        claimDependencies = nexusClaimCommandDependencies(),
        enableClaimFlag = lazy { getBean<net.lumalyte.lg.application.actions.claim.flag.EnableClaimFlag>() },
    )

internal fun NexusContext.nexusBedrockCacheStatsCommand(): net.lumalyte.lg.interaction.commands.BedrockCacheStatsCommand =
    net.lumalyte.lg.interaction.commands.BedrockCacheStatsCommand(
        lang = lazy { getBean<net.badgersmc.nexus.i18n.LangService>() },
        formCacheService = lazy { getBean<net.lumalyte.lg.application.services.FormCacheService>() },
    )

internal fun NexusContext.nexusClaimCommand(): net.lumalyte.lg.interaction.commands.ClaimCommand =
    net.lumalyte.lg.interaction.commands.ClaimCommand(
        claimDependencies = nexusClaimCommandDependencies(),
    )

internal fun NexusContext.nexusClaimCommandDependencies(): net.lumalyte.lg.interaction.commands.ClaimCommandDependencies =
    net.lumalyte.lg.interaction.commands.ClaimCommandDependencies(
        lang = lazy { getBean<net.badgersmc.nexus.i18n.LangService>() },
        getPartitionByPosition = lazy { getBean<net.lumalyte.lg.application.actions.claim.partition.GetPartitionByPosition>() },
        doesPlayerHaveClaimOverride = lazy { getBean<net.lumalyte.lg.application.actions.player.DoesPlayerHaveClaimOverride>() },
        getClaimDetails = lazy { getBean<net.lumalyte.lg.application.actions.claim.metadata.GetClaimDetails>() },
        givePlayerClaimTool = lazy { getBean<net.lumalyte.lg.application.actions.player.tool.GivePlayerClaimTool>() },
        claimManagementAuthorizer = lazy { getBean<net.lumalyte.lg.application.services.ClaimManagementAuthorizer>() },
    )

internal fun NexusContext.nexusClaimListCommand(): net.lumalyte.lg.interaction.commands.ClaimListCommand =
    net.lumalyte.lg.interaction.commands.ClaimListCommand(
        lang = lazy { getBean<net.badgersmc.nexus.i18n.LangService>() },
        listPlayerClaims = lazy { getBean<net.lumalyte.lg.application.actions.claim.ListPlayerClaims>() },
        getClaimBlockCount = lazy { getBean<net.lumalyte.lg.application.actions.claim.metadata.GetClaimBlockCount>() },
    )

internal fun NexusContext.nexusClaimMenuCommand(): net.lumalyte.lg.interaction.commands.ClaimMenuCommand =
    net.lumalyte.lg.interaction.commands.ClaimMenuCommand(
        menuFactory = lazy { getBean<net.lumalyte.lg.interaction.menus.MenuFactory>() },
    )

internal fun NexusContext.nexusClaimOverrideCommand(): net.lumalyte.lg.interaction.commands.ClaimOverrideCommand =
    net.lumalyte.lg.interaction.commands.ClaimOverrideCommand(
        lang = lazy { getBean<net.badgersmc.nexus.i18n.LangService>() },
        toggleClaimOverride = lazy { getBean<net.lumalyte.lg.application.actions.player.ToggleClaimOverride>() },
    )

internal fun NexusContext.nexusDescriptionCommand(): net.lumalyte.lg.interaction.commands.DescriptionCommand =
    net.lumalyte.lg.interaction.commands.DescriptionCommand(
        claimDependencies = nexusClaimCommandDependencies(),
        updateClaimDescription = lazy { getBean<net.lumalyte.lg.application.actions.claim.metadata.UpdateClaimDescription>() },
    )

internal fun NexusContext.nexusFullscreenAnnounceCommand(): net.lumalyte.lg.interaction.commands.FullscreenAnnounceCommand =
    net.lumalyte.lg.interaction.commands.FullscreenAnnounceCommand(
        guilds = lazy { getBean<net.lumalyte.lg.application.services.GuildService>() },
        members = lazy { getBean<net.lumalyte.lg.application.services.MemberService>() },
        service = lazy { getBean<net.lumalyte.lg.application.services.ChatService>() },
        penalties = lazy { getBean<net.lumalyte.lg.application.services.PenaltyService>() },
        lang = lazy { getBean<net.badgersmc.nexus.i18n.LangService>() },
    )

internal fun NexusContext.nexusGuildCommand(): net.lumalyte.lg.interaction.commands.GuildCommand =
    net.lumalyte.lg.interaction.commands.GuildCommand(
        dependencies = nexusGuildCommandDependencies(),
    )

internal fun NexusContext.nexusGuildCommandDependencies(): net.lumalyte.lg.interaction.commands.GuildCommandDependencies =
    net.lumalyte.lg.interaction.commands.GuildCommandDependencies(
        lang = lazy { getBean<net.badgersmc.nexus.i18n.LangService>() },
        guildService = lazy { getBean<net.lumalyte.lg.application.services.GuildService>() },
        lfgService = lazy { getBean<net.lumalyte.lg.application.services.LfgService>() },
        guildRepository = lazy { getBean<net.lumalyte.lg.application.persistence.GuildRepository>() },
        rankService = lazy { getBean<net.lumalyte.lg.application.services.RankService>() },
        chatRankSettings = lazy { getBean<net.lumalyte.lg.application.services.GuildChatRankSettingsService>() },
        memberService = lazy { getBean<net.lumalyte.lg.application.services.MemberService>() },
        inviteBlocks = lazy { getBean<net.lumalyte.lg.application.services.InviteBlockService>() },
        vaultService = lazy { getBean<net.lumalyte.lg.application.services.GuildVaultService>() },
        warService = lazy { getBean<net.lumalyte.lg.application.services.WarService>() },
        configService = lazy { getBean<net.lumalyte.lg.application.services.ConfigService>() },
        menuFactory = lazy { getBean<net.lumalyte.lg.interaction.menus.MenuFactory>() },
        progressionService = lazy { getBean<net.lumalyte.lg.application.services.ProgressionService>() },
        historyRepository = lazy { getBean<net.lumalyte.lg.application.persistence.MembershipHistoryRepository>() },
        guildChatListener = lazy { getBean<net.lumalyte.lg.interaction.listeners.GuildChatListener>() },
        adminOverrideService = lazy { getBean<net.lumalyte.lg.application.services.AdminOverrideService>() },
        teleportationService = lazy { getBean<net.lumalyte.lg.infrastructure.services.TeleportationService>() },
        bannermanListeners = lazy { getBean<net.lumalyte.lg.infrastructure.bukkit.bannerman.BannermanListeners>() },
        strikeService = lazy { getBean<net.lumalyte.lg.application.services.StrikeService>() },
        bankService = lazy { getBean<net.lumalyte.lg.application.services.BankService>() },
        guildCostService = lazy { getBean<net.lumalyte.lg.application.services.GuildCostService>() },
        guildHomeActivationService = lazy { getBean<net.lumalyte.lg.application.services.GuildHomeActivationService>() },
        warBannerService = lazy { getBean<net.lumalyte.lg.infrastructure.services.WarBannerServiceBukkit>() },
        guildLoginNotificationService = lazy { getBean<net.lumalyte.lg.application.services.GuildLoginNotificationService>() },
        relationService = lazy { getBean<net.lumalyte.lg.application.services.RelationService>() },
        nexoEmojiService = lazy { getBean<net.lumalyte.lg.infrastructure.services.NexoEmojiService>() },
        guildRecruitmentService = lazy { getBean<net.lumalyte.lg.application.services.GuildRecruitmentService>() },
        guildOnboardingRepository = lazy { getBean<net.lumalyte.lg.application.persistence.GuildOnboardingRepository>() },
    )

internal fun NexusContext.nexusInfoCommand(): net.lumalyte.lg.interaction.commands.InfoCommand =
    net.lumalyte.lg.interaction.commands.InfoCommand(
        claimDependencies = nexusClaimCommandDependencies(),
        getClaimFlags = lazy { getBean<net.lumalyte.lg.application.actions.claim.flag.GetClaimFlags>() },
        getClaimPermissions = lazy { getBean<net.lumalyte.lg.application.actions.claim.permission.GetClaimPermissions>() },
        getClaimPartitions = lazy { getBean<net.lumalyte.lg.application.actions.claim.partition.GetClaimPartitions>() },
        getClaimBlockCount = lazy { getBean<net.lumalyte.lg.application.actions.claim.metadata.GetClaimBlockCount>() },
        getPlayersWithPermissionInClaim = lazy { getBean<net.lumalyte.lg.application.actions.claim.permission.GetPlayersWithPermissionInClaim>() },
    )

internal fun NexusContext.nexusLumaGuildsCommand(): net.lumalyte.lg.interaction.commands.LumaGuildsCommand =
    net.lumalyte.lg.interaction.commands.LumaGuildsCommand(
        dependencies = nexusLumaGuildsCommandDependencies(),
    )

internal fun NexusContext.nexusLumaGuildsCommandDependencies(): net.lumalyte.lg.interaction.commands.LumaGuildsCommandDependencies =
    net.lumalyte.lg.interaction.commands.LumaGuildsCommandDependencies(
        lang = lazy { getBean<net.badgersmc.nexus.i18n.LangService>() },
        guildService = lazy { getBean<net.lumalyte.lg.application.services.GuildService>() },
        adminOverrideService = lazy { getBean<net.lumalyte.lg.application.services.AdminOverrideService>() },
        storage = lazy { getBean<net.lumalyte.lg.infrastructure.persistence.storage.Storage<co.aikar.idb.Database>>() },
        spawnBannerService = lazy { getBean<net.lumalyte.lg.infrastructure.services.SpawnBannerServiceBukkit>() },
        warService = lazy { getBean<net.lumalyte.lg.application.services.WarService>() },
        progressionService = lazy { getBean<net.lumalyte.lg.application.services.ProgressionService>() },
        memberService = lazy { getBean<net.lumalyte.lg.application.services.MemberService>() },
        menuFactory = lazy { getBean<net.lumalyte.lg.interaction.menus.MenuFactory>() },
        progressionConfigService = lazy { getBean<net.lumalyte.lg.infrastructure.services.ProgressionConfigService>() },
        guildEmojiGrantService = lazy { getBean<net.lumalyte.lg.infrastructure.services.GuildEmojiGrantService>() },
        guildRolePermissionResolver = { if (containsBean("guildRolePermissionResolver")) getBean<net.lumalyte.lg.application.services.GuildRolePermissionResolver>() else null },
        progressionEventListener = { if (containsBean("progressionEventListener")) getBean<net.lumalyte.lg.infrastructure.listeners.ProgressionEventListener>() else null },
    )

internal fun NexusContext.nexusPartitionsCommand(): net.lumalyte.lg.interaction.commands.PartitionsCommand =
    net.lumalyte.lg.interaction.commands.PartitionsCommand(
        claimDependencies = nexusClaimCommandDependencies(),
        getClaimPartitions = lazy { getBean<net.lumalyte.lg.application.actions.claim.partition.GetClaimPartitions>() },
    )

internal fun NexusContext.nexusPartyChatCommand(): net.lumalyte.lg.interaction.commands.PartyChatCommand =
    net.lumalyte.lg.interaction.commands.PartyChatCommand(
        dependencies = nexusPartyChatCommandDependencies(),
    )

internal fun NexusContext.nexusPartyChatCommandDependencies(): net.lumalyte.lg.interaction.commands.PartyChatCommandDependencies =
    net.lumalyte.lg.interaction.commands.PartyChatCommandDependencies(
        lang = lazy { getBean<net.badgersmc.nexus.i18n.LangService>() },
        chatService = lazy { getBean<net.lumalyte.lg.application.services.ChatService>() },
        partyService = lazy { getBean<net.lumalyte.lg.application.services.PartyService>() },
        guildService = lazy { getBean<net.lumalyte.lg.application.services.GuildService>() },
        memberService = lazy { getBean<net.lumalyte.lg.application.services.MemberService>() },
        partyRepository = lazy { getBean<net.lumalyte.lg.application.persistence.PartyRepository>() },
        preferenceRepository = lazy { getBean<net.lumalyte.lg.application.persistence.PlayerPartyPreferenceRepository>() },
        configService = lazy { getBean<net.lumalyte.lg.application.services.ConfigService>() },
    )

internal fun NexusContext.nexusQuickAllyChatCommand(): net.lumalyte.lg.interaction.commands.QuickAllyChatCommand =
    net.lumalyte.lg.interaction.commands.QuickAllyChatCommand(
        lang = lazy { getBean<net.badgersmc.nexus.i18n.LangService>() },
        guildService = lazy { getBean<net.lumalyte.lg.application.services.GuildService>() },
    )

internal fun NexusContext.nexusQuickAnnounceCommand(): net.lumalyte.lg.interaction.commands.QuickAnnounceCommand =
    net.lumalyte.lg.interaction.commands.QuickAnnounceCommand(
        lang = lazy { getBean<net.badgersmc.nexus.i18n.LangService>() },
        chatService = lazy { getBean<net.lumalyte.lg.application.services.ChatService>() },
        guildService = lazy { getBean<net.lumalyte.lg.application.services.GuildService>() },
        memberService = lazy { getBean<net.lumalyte.lg.application.services.MemberService>() },
    )

internal fun NexusContext.nexusQuickGuildChatCommand(): net.lumalyte.lg.interaction.commands.QuickGuildChatCommand =
    net.lumalyte.lg.interaction.commands.QuickGuildChatCommand(
        lang = lazy { getBean<net.badgersmc.nexus.i18n.LangService>() },
        guildService = lazy { getBean<net.lumalyte.lg.application.services.GuildService>() },
        penaltyService = lazy { getBean<net.lumalyte.lg.application.services.PenaltyService>() },
    )

internal fun NexusContext.nexusQuickModChatCommand(): net.lumalyte.lg.interaction.commands.QuickModChatCommand =
    net.lumalyte.lg.interaction.commands.QuickModChatCommand(
        lang = lazy { getBean<net.badgersmc.nexus.i18n.LangService>() },
        guildService = lazy { getBean<net.lumalyte.lg.application.services.GuildService>() },
        memberService = lazy { getBean<net.lumalyte.lg.application.services.MemberService>() },
    )

internal fun NexusContext.nexusRemoveCommand(): net.lumalyte.lg.interaction.commands.RemoveCommand =
    net.lumalyte.lg.interaction.commands.RemoveCommand(
        claimDependencies = nexusClaimCommandDependencies(),
        removePartition = lazy { getBean<net.lumalyte.lg.application.actions.claim.partition.RemovePartition>() },
    )

internal fun NexusContext.nexusRemoveFlagCommand(): net.lumalyte.lg.interaction.commands.RemoveFlagCommand =
    net.lumalyte.lg.interaction.commands.RemoveFlagCommand(
        claimDependencies = nexusClaimCommandDependencies(),
        disableClaimFlag = lazy { getBean<net.lumalyte.lg.application.actions.claim.flag.DisableClaimFlag>() },
    )

internal fun NexusContext.nexusRenameCommand(): net.lumalyte.lg.interaction.commands.RenameCommand =
    net.lumalyte.lg.interaction.commands.RenameCommand(
        claimDependencies = nexusClaimCommandDependencies(),
        updateClaimName = lazy { getBean<net.lumalyte.lg.application.actions.claim.metadata.UpdateClaimName>() },
    )

internal fun NexusContext.nexusTrustAllCommand(): net.lumalyte.lg.interaction.commands.TrustAllCommand =
    net.lumalyte.lg.interaction.commands.TrustAllCommand(
        claimDependencies = nexusClaimCommandDependencies(),
        grantClaimWidePermission = lazy { getBean<net.lumalyte.lg.application.actions.claim.permission.GrantClaimWidePermission>() },
    )

internal fun NexusContext.nexusTrustCommand(): net.lumalyte.lg.interaction.commands.TrustCommand =
    net.lumalyte.lg.interaction.commands.TrustCommand(
        claimDependencies = nexusClaimCommandDependencies(),
        grantPlayerClaimPermission = lazy { getBean<net.lumalyte.lg.application.actions.claim.permission.GrantPlayerClaimPermission>() },
    )

internal fun NexusContext.nexusTrustListCommand(): net.lumalyte.lg.interaction.commands.TrustListCommand =
    net.lumalyte.lg.interaction.commands.TrustListCommand(
        claimDependencies = nexusClaimCommandDependencies(),
        getPlayersWithPermissionInClaim = lazy { getBean<net.lumalyte.lg.application.actions.claim.permission.GetPlayersWithPermissionInClaim>() },
        getClaimPlayerPermissions = lazy { getBean<net.lumalyte.lg.application.actions.claim.permission.GetClaimPlayerPermissions>() },
    )

internal fun NexusContext.nexusUntrustAllCommand(): net.lumalyte.lg.interaction.commands.UntrustAllCommand =
    net.lumalyte.lg.interaction.commands.UntrustAllCommand(
        claimDependencies = nexusClaimCommandDependencies(),
        revokeClaimWidePermission = lazy { getBean<net.lumalyte.lg.application.actions.claim.permission.RevokeClaimWidePermission>() },
    )

internal fun NexusContext.nexusUntrustCommand(): net.lumalyte.lg.interaction.commands.UntrustCommand =
    net.lumalyte.lg.interaction.commands.UntrustCommand(
        claimDependencies = nexusClaimCommandDependencies(),
        revokePlayerClaimPermission = lazy { getBean<net.lumalyte.lg.application.actions.claim.permission.RevokePlayerClaimPermission>() },
    )

internal fun NexusContext.nexusBannerSelectionListener(): net.lumalyte.lg.interaction.listeners.BannerSelectionListener =
    net.lumalyte.lg.interaction.listeners.BannerSelectionListener(
        guildService = lazy { getBean<net.lumalyte.lg.application.services.GuildService>() },
        lang = lazy { getBean<net.badgersmc.nexus.i18n.LangService>() },
    )

internal fun NexusContext.nexusClaimAnchorListener(): net.lumalyte.lg.interaction.listeners.ClaimAnchorListener =
    net.lumalyte.lg.interaction.listeners.ClaimAnchorListener(
        dependencies = nexusClaimAnchorListenerDependencies(),
    )

internal fun NexusContext.nexusClaimAnchorListenerDependencies(): net.lumalyte.lg.interaction.listeners.ClaimAnchorListenerDependencies =
    net.lumalyte.lg.interaction.listeners.ClaimAnchorListenerDependencies(
        lang = lazy { getBean<net.badgersmc.nexus.i18n.LangService>() },
        getClaimAnchorAtPosition = lazy { getBean<net.lumalyte.lg.application.actions.claim.anchor.GetClaimAnchorAtPosition>() },
        createClaim = lazy { getBean<net.lumalyte.lg.application.actions.claim.CreateClaim>() },
        doesPlayerHaveTransferRequest = lazy { getBean<net.lumalyte.lg.application.actions.claim.transfer.DoesPlayerHaveTransferRequest>() },
        doesPlayerHaveClaimOverride = lazy { getBean<net.lumalyte.lg.application.actions.player.DoesPlayerHaveClaimOverride>() },
        claimManagementAuthorizer = lazy { getBean<net.lumalyte.lg.application.services.ClaimManagementAuthorizer>() },
        menuFactory = lazy { getBean<net.lumalyte.lg.interaction.menus.MenuFactory>() },
    )

internal fun NexusContext.nexusClaimDestructionListener(): net.lumalyte.lg.interaction.listeners.ClaimDestructionListener =
    net.lumalyte.lg.interaction.listeners.ClaimDestructionListener(
        lang = lazy { getBean<net.badgersmc.nexus.i18n.LangService>() },
        getClaimAnchorAtPosition = lazy { getBean<net.lumalyte.lg.application.actions.claim.anchor.GetClaimAnchorAtPosition>() },
        breakClaimAnchor = lazy { getBean<net.lumalyte.lg.application.actions.claim.anchor.BreakClaimAnchor>() },
        doesPlayerHaveClaimOverride = lazy { getBean<net.lumalyte.lg.application.actions.player.DoesPlayerHaveClaimOverride>() },
        claimManagementAuthorizer = lazy { getBean<net.lumalyte.lg.application.services.ClaimManagementAuthorizer>() },
    )

internal fun NexusContext.nexusClaimPvpProtectionListener(): net.lumalyte.lg.interaction.listeners.ClaimPvpProtectionListener =
    net.lumalyte.lg.interaction.listeners.ClaimPvpProtectionListener(
        combatService = lazy { getBean<net.lumalyte.lg.application.services.CombatService>() },
        getClaimAtPosition = lazy { getBean<net.lumalyte.lg.application.actions.claim.GetClaimAtPosition>() },
        guildService = lazy { getBean<net.lumalyte.lg.application.services.GuildService>() },
    )

internal fun NexusContext.nexusCloseInventoryListener(): net.lumalyte.lg.interaction.listeners.CloseInventoryListener =
    net.lumalyte.lg.interaction.listeners.CloseInventoryListener(
        unregisterClaimMenuOpening = lazy { getBean<net.lumalyte.lg.application.actions.player.UnregisterClaimMenuOpening>() },
    )

internal fun NexusContext.nexusEditToolListener(): net.lumalyte.lg.interaction.listeners.EditToolListener =
    net.lumalyte.lg.interaction.listeners.EditToolListener(
        dependencies = nexusEditToolListenerDependencies(),
    )

internal fun NexusContext.nexusEditToolListenerDependencies(): net.lumalyte.lg.interaction.listeners.EditToolListenerDependencies =
    net.lumalyte.lg.interaction.listeners.EditToolListenerDependencies(
        lang = lazy { getBean<net.badgersmc.nexus.i18n.LangService>() },
        getPartitionByPosition = lazy { getBean<net.lumalyte.lg.application.actions.claim.partition.GetPartitionByPosition>() },
        displayVisualisation = lazy { getBean<net.lumalyte.lg.application.actions.player.visualisation.DisplayVisualisation>() },
        createPartition = lazy { getBean<net.lumalyte.lg.application.actions.claim.partition.CreatePartition>() },
        getRemainingClaimBlockCount = lazy { getBean<net.lumalyte.lg.application.actions.player.GetRemainingClaimBlockCount>() },
        getClaimAtPosition = lazy { getBean<net.lumalyte.lg.application.actions.claim.GetClaimAtPosition>() },
        doesPlayerHaveClaimOverride = lazy { getBean<net.lumalyte.lg.application.actions.player.DoesPlayerHaveClaimOverride>() },
        resizePartition = lazy { getBean<net.lumalyte.lg.application.actions.claim.partition.ResizePartition>() },
        isItemClaimTool = lazy { getBean<net.lumalyte.lg.application.actions.player.tool.IsItemClaimTool>() },
        coroutineScope = lazy { getBean<kotlinx.coroutines.CoroutineScope>() },
        menuFactory = lazy { getBean<net.lumalyte.lg.interaction.menus.MenuFactory>() },
        schedulerService = lazy { getBean<net.lumalyte.lg.application.services.scheduling.SchedulerService>() },
        displaySelectionVisualisation = lazy { getBean<net.lumalyte.lg.application.actions.player.visualisation.DisplaySelectionVisualisation>() },
        clearSelectionVisualisation = lazy { getBean<net.lumalyte.lg.application.actions.player.visualisation.ClearSelectionVisualisation>() },
        refreshVisualisation = lazy { getBean<net.lumalyte.lg.application.actions.player.visualisation.RefreshVisualisation>() },
        claimManagementAuthorizer = lazy { getBean<net.lumalyte.lg.application.services.ClaimManagementAuthorizer>() },
    )

internal fun NexusContext.nexusHarvestReplantListener(): net.lumalyte.lg.interaction.listeners.HarvestReplantListener =
    net.lumalyte.lg.interaction.listeners.HarvestReplantListener(
        config = lazy { getBean<net.lumalyte.lg.config.MainConfig>() },
    )

internal fun NexusContext.nexusMoveToolListener(): net.lumalyte.lg.interaction.listeners.MoveToolListener =
    net.lumalyte.lg.interaction.listeners.MoveToolListener(
        lang = lazy { getBean<net.badgersmc.nexus.i18n.LangService>() },
        moveClaimAnchor = lazy { getBean<net.lumalyte.lg.application.actions.claim.anchor.MoveClaimAnchor>() },
        getClaimIdFromMoveTool = lazy { getBean<net.lumalyte.lg.application.actions.player.tool.GetClaimIdFromMoveTool>() },
    )

internal fun NexusContext.nexusPartitionUpdateListener(): net.lumalyte.lg.interaction.listeners.PartitionUpdateListener =
    net.lumalyte.lg.interaction.listeners.PartitionUpdateListener(
        getClaimDetails = lazy { getBean<net.lumalyte.lg.application.actions.claim.metadata.GetClaimDetails>() },
        getClaimPartitions = lazy { getBean<net.lumalyte.lg.application.actions.claim.partition.GetClaimPartitions>() },
        isPlayerVisualising = lazy { getBean<net.lumalyte.lg.application.actions.player.visualisation.IsPlayerVisualising>() },
        refreshVisualisation = lazy { getBean<net.lumalyte.lg.application.actions.player.visualisation.RefreshVisualisation>() },
    )

internal fun NexusContext.nexusPartyChatListener(): net.lumalyte.lg.interaction.listeners.PartyChatListener =
    net.lumalyte.lg.interaction.listeners.PartyChatListener(
        dependencies = nexusPartyChatListenerDependencies(),
    )

internal fun NexusContext.nexusPartyChatListenerDependencies(): net.lumalyte.lg.interaction.listeners.PartyChatListenerDependencies =
    net.lumalyte.lg.interaction.listeners.PartyChatListenerDependencies(
        chatService = lazy { getBean<net.lumalyte.lg.application.services.ChatService>() },
        preferenceRepository = lazy { getBean<net.lumalyte.lg.application.persistence.PlayerPartyPreferenceRepository>() },
        partyRepository = lazy { getBean<net.lumalyte.lg.application.persistence.PartyRepository>() },
        guildService = lazy { getBean<net.lumalyte.lg.application.services.GuildService>() },
        memberService = lazy { getBean<net.lumalyte.lg.application.services.MemberService>() },
        configService = lazy { getBean<net.lumalyte.lg.application.services.ConfigService>() },
        chatInputListener = lazy { getBean<net.lumalyte.lg.interaction.listeners.ChatInputListener>() },
        plugin = lazy { getBean<org.bukkit.plugin.Plugin>() },
        lang = lazy { getBean<net.badgersmc.nexus.i18n.LangService>() },
    )

internal fun NexusContext.nexusPlayerClaimProtectionListener(): net.lumalyte.lg.interaction.listeners.PlayerClaimProtectionListener =
    net.lumalyte.lg.interaction.listeners.PlayerClaimProtectionListener(
        lang = lazy { getBean<net.badgersmc.nexus.i18n.LangService>() },
        isPlayerActionAllowed = lazy { getBean<net.lumalyte.lg.application.actions.claim.IsPlayerActionAllowed>() },
    )

internal fun NexusContext.nexusToolRemovalListener(): net.lumalyte.lg.interaction.listeners.ToolRemovalListener =
    net.lumalyte.lg.interaction.listeners.ToolRemovalListener(
        isItemClaimTool = lazy { getBean<net.lumalyte.lg.application.actions.player.tool.IsItemClaimTool>() },
        isItemMoveTool = lazy { getBean<net.lumalyte.lg.application.actions.player.tool.IsItemMoveTool>() },
    )

internal fun NexusContext.nexusWorldClaimProtectionListener(): net.lumalyte.lg.interaction.listeners.WorldClaimProtectionListener =
    net.lumalyte.lg.interaction.listeners.WorldClaimProtectionListener(
        isWorldActionAllowed = lazy { getBean<net.lumalyte.lg.application.actions.claim.IsWorldActionAllowed>() },
        getClaimAtPosition = lazy { getBean<net.lumalyte.lg.application.actions.claim.GetClaimAtPosition>() },
    )
