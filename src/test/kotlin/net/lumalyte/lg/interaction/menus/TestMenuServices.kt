package net.lumalyte.lg.interaction.menus

import io.mockk.mockk
import kotlin.reflect.KClass

/**
 * Test fixture: a [MenuServices] bundle whose collaborators are relaxed MockK mocks unless overridden by type.
 * Nothing is created until a menu reads it, mirroring production laziness. No Koin is started.
 */
internal fun testMenuServices(
    factory: MenuFactory = mockk(relaxed = true),
    overrides: Map<KClass<*>, Any?> = emptyMap(),
): MenuServices {
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> pick(type: KClass<T>, create: () -> T): Lazy<T> =
        lazy { if (type in overrides) overrides[type] as T else create() }
    return MenuServices(
        platformDetectionService = pick(net.lumalyte.lg.application.services.PlatformDetectionService::class) { mockk(relaxed = true) },
        acceptTransferRequest = pick(net.lumalyte.lg.application.actions.claim.transfer.AcceptTransferRequest::class) { mockk(relaxed = true) },
        adminOverrideService = pick(net.lumalyte.lg.application.services.AdminOverrideService::class) { mockk(relaxed = true) },
        bankAutomationService = pick(net.lumalyte.lg.application.services.BankAutomationService::class) { mockk(relaxed = true) },
        bankService = pick(net.lumalyte.lg.application.services.BankService::class) { mockk(relaxed = true) },
        bankSettingsRepository = pick(net.lumalyte.lg.application.persistence.BankSettingsRepository::class) { mockk(relaxed = true) },
        bannerPurchaseService = pick(net.lumalyte.lg.application.services.BannerPurchaseService::class) { mockk(relaxed = true) },
        bedrockLocalizationService = pick(net.lumalyte.lg.application.services.BedrockLocalizationService::class) { mockk(relaxed = true) },
        bukkitPhysicalGoldAdapter = pick(net.lumalyte.lg.infrastructure.services.BukkitPhysicalGoldAdapter::class) { mockk(relaxed = true) },
        canPlayerReceiveTransferRequest = pick(net.lumalyte.lg.application.actions.claim.transfer.CanPlayerReceiveTransferRequest::class) { mockk(relaxed = true) },
        canRemovePartition = pick(net.lumalyte.lg.application.actions.claim.partition.CanRemovePartition::class) { mockk(relaxed = true) },
        chatInputListener = pick(net.lumalyte.lg.interaction.listeners.ChatInputListener::class) { mockk(relaxed = true) },
        chatService = pick(net.lumalyte.lg.application.services.ChatService::class) { mockk(relaxed = true) },
        claimManagementAuthorizer = pick(net.lumalyte.lg.application.services.ClaimManagementAuthorizer::class) { mockk(relaxed = true) },
        claimRepository = pick(net.lumalyte.lg.application.persistence.ClaimRepository::class) { mockk(relaxed = true) },
        clearVisualisation = pick(net.lumalyte.lg.application.actions.player.visualisation.ClearVisualisation::class) { mockk(relaxed = true) },
        configService = pick(net.lumalyte.lg.application.services.ConfigService::class) { mockk(relaxed = true) },
        convertClaimToGuild = pick(net.lumalyte.lg.application.actions.claim.ConvertClaimToGuild::class) { mockk(relaxed = true) },
        createClaim = pick(net.lumalyte.lg.application.actions.claim.CreateClaim::class) { mockk(relaxed = true) },
        disableAllClaimFlags = pick(net.lumalyte.lg.application.actions.claim.flag.DisableAllClaimFlags::class) { mockk(relaxed = true) },
        disableClaimFlag = pick(net.lumalyte.lg.application.actions.claim.flag.DisableClaimFlag::class) { mockk(relaxed = true) },
        displayVisualisation = pick(net.lumalyte.lg.application.actions.player.visualisation.DisplayVisualisation::class) { mockk(relaxed = true) },
        doesPlayerHaveClaimOverride = pick(net.lumalyte.lg.application.actions.player.DoesPlayerHaveClaimOverride::class) { mockk(relaxed = true) },
        doesPlayerHaveTransferRequest = pick(net.lumalyte.lg.application.actions.claim.transfer.DoesPlayerHaveTransferRequest::class) { mockk(relaxed = true) },
        enableAllClaimFlags = pick(net.lumalyte.lg.application.actions.claim.flag.EnableAllClaimFlags::class) { mockk(relaxed = true) },
        enableClaimFlag = pick(net.lumalyte.lg.application.actions.claim.flag.EnableClaimFlag::class) { mockk(relaxed = true) },
        formCacheService = pick(net.lumalyte.lg.application.services.FormCacheService::class) { mockk(relaxed = true) },
        formValidationService = pick(net.lumalyte.lg.application.services.FormValidationService::class) { mockk(relaxed = true) },
        getClaimBlockCount = pick(net.lumalyte.lg.application.actions.claim.metadata.GetClaimBlockCount::class) { mockk(relaxed = true) },
        getClaimDetails = pick(net.lumalyte.lg.application.actions.claim.metadata.GetClaimDetails::class) { mockk(relaxed = true) },
        getClaimFlags = pick(net.lumalyte.lg.application.actions.claim.flag.GetClaimFlags::class) { mockk(relaxed = true) },
        getClaimPartitions = pick(net.lumalyte.lg.application.actions.claim.partition.GetClaimPartitions::class) { mockk(relaxed = true) },
        getClaimPermissions = pick(net.lumalyte.lg.application.actions.claim.permission.GetClaimPermissions::class) { mockk(relaxed = true) },
        getClaimPlayerPermissions = pick(net.lumalyte.lg.application.actions.claim.permission.GetClaimPlayerPermissions::class) { mockk(relaxed = true) },
        getPlayersWithPermissionInClaim = pick(net.lumalyte.lg.application.actions.claim.permission.GetPlayersWithPermissionInClaim::class) { mockk(relaxed = true) },
        getVisualiserMode = pick(net.lumalyte.lg.application.actions.player.visualisation.GetVisualiserMode::class) { mockk(relaxed = true) },
        givePlayerClaimTool = pick(net.lumalyte.lg.application.actions.player.tool.GivePlayerClaimTool::class) { mockk(relaxed = true) },
        givePlayerMoveTool = pick(net.lumalyte.lg.application.actions.player.tool.GivePlayerMoveTool::class) { mockk(relaxed = true) },
        grantAllClaimWidePermissions = pick(net.lumalyte.lg.application.actions.claim.permission.GrantAllClaimWidePermissions::class) { mockk(relaxed = true) },
        grantAllPlayerClaimPermissions = pick(net.lumalyte.lg.application.actions.claim.permission.GrantAllPlayerClaimPermissions::class) { mockk(relaxed = true) },
        grantClaimWidePermission = pick(net.lumalyte.lg.application.actions.claim.permission.GrantClaimWidePermission::class) { mockk(relaxed = true) },
        grantPlayerClaimPermission = pick(net.lumalyte.lg.application.actions.claim.permission.GrantPlayerClaimPermission::class) { mockk(relaxed = true) },
        guildAchievementRuntime = pick(net.lumalyte.lg.infrastructure.services.GuildAchievementRuntime::class) { mockk(relaxed = true) },
        guildChatRankSettingsService = pick(net.lumalyte.lg.application.services.GuildChatRankSettingsService::class) { mockk(relaxed = true) },
        guildChatReconnectSettingsService = pick(net.lumalyte.lg.application.services.GuildChatReconnectSettingsService::class) { mockk(relaxed = true) },
        guildChronicleService = pick(net.lumalyte.lg.application.services.GuildChronicleService::class) { mockk(relaxed = true) },
        guildCosmeticUnlockService = pick(net.lumalyte.lg.application.services.GuildCosmeticUnlockService::class) { mockk(relaxed = true) },
        guildHomeActivationService = pick(net.lumalyte.lg.application.services.GuildHomeActivationService::class) { mockk(relaxed = true) },
        guildListService = pick(net.lumalyte.lg.application.services.GuildListService::class) { mockk(relaxed = true) },
        guildPrestigeService = pick(net.lumalyte.lg.application.services.GuildPrestigeService::class) { mockk(relaxed = true) },
        guildRecruitmentService = pick(net.lumalyte.lg.application.services.GuildRecruitmentService::class) { mockk(relaxed = true) },
        guildRepository = pick(net.lumalyte.lg.application.persistence.GuildRepository::class) { mockk(relaxed = true) },
        guildRewardPurchaseService = pick(net.lumalyte.lg.application.services.GuildRewardPurchaseService::class) { mockk(relaxed = true) },
        guildService = pick(net.lumalyte.lg.application.services.GuildService::class) { mockk(relaxed = true) },
        guildVaultService = pick(net.lumalyte.lg.application.services.GuildVaultService::class) { mockk(relaxed = true) },
        invitationStatisticsService = pick(net.lumalyte.lg.application.services.InvitationStatisticsService::class) { mockk(relaxed = true) },
        isNewClaimLocationValid = pick(net.lumalyte.lg.application.actions.claim.IsNewClaimLocationValid::class) { mockk(relaxed = true) },
        isPlayerInClaimMenu = pick(net.lumalyte.lg.application.actions.player.IsPlayerInClaimMenu::class) { mockk(relaxed = true) },
        killService = pick(net.lumalyte.lg.application.services.KillService::class) { mockk(relaxed = true) },
        langService = pick(net.badgersmc.nexus.i18n.LangService::class) { mockk(relaxed = true) },
        leaderboardService = pick(net.lumalyte.lg.application.services.LeaderboardService::class) { mockk(relaxed = true) },
        lfgService = pick(net.lumalyte.lg.application.services.LfgService::class) { mockk(relaxed = true) },
        listPlayerClaims = pick(net.lumalyte.lg.application.actions.claim.ListPlayerClaims::class) { mockk(relaxed = true) },
        lumaGuilds = pick(net.lumalyte.lg.LumaGuilds::class) { mockk(relaxed = true) },
        memberService = pick(net.lumalyte.lg.application.services.MemberService::class) { mockk(relaxed = true) },
        menuFactory = lazyOf(factory),
        menuItemBuilder = pick(net.lumalyte.lg.utils.MenuItemBuilder::class) { mockk(relaxed = true) },
        nexoEmojiService = pick(net.lumalyte.lg.infrastructure.services.NexoEmojiService::class) { mockk(relaxed = true) },
        offerPlayerTransferRequest = pick(net.lumalyte.lg.application.actions.claim.transfer.OfferPlayerTransferRequest::class) { mockk(relaxed = true) },
        partyService = pick(net.lumalyte.lg.application.services.PartyService::class) { mockk(relaxed = true) },
        penaltyService = pick(net.lumalyte.lg.application.services.PenaltyService::class) { mockk(relaxed = true) },
        physicalCurrencyService = pick(net.lumalyte.lg.application.services.PhysicalCurrencyService::class) { mockk(relaxed = true) },
        playerMetadataService = pick(net.lumalyte.lg.application.services.PlayerMetadataService::class) { mockk(relaxed = true) },
        plugin = pick(org.bukkit.plugin.Plugin::class) { mockk(relaxed = true) },
        progressionRepository = pick(net.lumalyte.lg.application.persistence.ProgressionRepository::class) { mockk(relaxed = true) },
        progressionService = pick(net.lumalyte.lg.application.services.ProgressionService::class) { mockk(relaxed = true) },
        questService = pick(net.lumalyte.lg.application.services.QuestService::class) { mockk(relaxed = true) },
        rankService = pick(net.lumalyte.lg.application.services.RankService::class) { mockk(relaxed = true) },
        recruitmentDirectoryRepository = pick(net.lumalyte.lg.application.persistence.RecruitmentDirectoryRepository::class) { mockk(relaxed = true) },
        registerClaimMenuOpening = pick(net.lumalyte.lg.application.actions.player.RegisterClaimMenuOpening::class) { mockk(relaxed = true) },
        relationRepository = pick(net.lumalyte.lg.application.persistence.RelationRepository::class) { mockk(relaxed = true) },
        relationService = pick(net.lumalyte.lg.application.services.RelationService::class) { mockk(relaxed = true) },
        removePartition = pick(net.lumalyte.lg.application.actions.claim.partition.RemovePartition::class) { mockk(relaxed = true) },
        revokeAllClaimWidePermissions = pick(net.lumalyte.lg.application.actions.claim.permission.RevokeAllClaimWidePermissions::class) { mockk(relaxed = true) },
        revokeAllPlayerClaimPermissions = pick(net.lumalyte.lg.application.actions.claim.permission.RevokeAllPlayerClaimPermissions::class) { mockk(relaxed = true) },
        revokeClaimWidePermission = pick(net.lumalyte.lg.application.actions.claim.permission.RevokeClaimWidePermission::class) { mockk(relaxed = true) },
        revokePlayerClaimPermission = pick(net.lumalyte.lg.application.actions.claim.permission.RevokePlayerClaimPermission::class) { mockk(relaxed = true) },
        schedulerService = pick(net.lumalyte.lg.application.services.scheduling.SchedulerService::class) { mockk(relaxed = true) },
        seasonalEloCoordinator = pick(net.lumalyte.lg.infrastructure.services.SeasonalEloCoordinator::class) { mockk(relaxed = true) },
        strikeService = pick(net.lumalyte.lg.application.services.StrikeService::class) { mockk(relaxed = true) },
        teleportationService = pick(net.lumalyte.lg.infrastructure.services.TeleportationService::class) { mockk(relaxed = true) },
        toggleVisualiserMode = pick(net.lumalyte.lg.application.actions.player.visualisation.ToggleVisualiserMode::class) { mockk(relaxed = true) },
        updateClaimIcon = pick(net.lumalyte.lg.application.actions.claim.metadata.UpdateClaimIcon::class) { mockk(relaxed = true) },
        updateClaimName = pick(net.lumalyte.lg.application.actions.claim.metadata.UpdateClaimName::class) { mockk(relaxed = true) },
        vaultInventoryManager = pick(net.lumalyte.lg.infrastructure.vault.VaultInventoryManager::class) { mockk(relaxed = true) },
        warService = pick(net.lumalyte.lg.application.services.WarService::class) { mockk(relaxed = true) },
        withdrawPlayerTransferRequest = pick(net.lumalyte.lg.application.actions.claim.transfer.WithdrawPlayerTransferRequest::class) { mockk(relaxed = true) },
        adminOverrideServiceOrNull = lazy {
            overrides[net.lumalyte.lg.application.services.AdminOverrideService::class]
                as net.lumalyte.lg.application.services.AdminOverrideService?
        },
        guildCosmeticUnlockServiceOrNull = lazy {
            overrides[net.lumalyte.lg.application.services.GuildCosmeticUnlockService::class]
                as net.lumalyte.lg.application.services.GuildCosmeticUnlockService?
        },
        guildServiceOrNull = lazy {
            overrides[net.lumalyte.lg.application.services.GuildService::class]
                as net.lumalyte.lg.application.services.GuildService?
        },
    )
}
