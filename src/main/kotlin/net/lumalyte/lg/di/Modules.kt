package net.lumalyte.lg.di

import net.lumalyte.lg.LumaGuilds
import net.lumalyte.lg.application.actions.claim.ConvertClaimToGuild
import net.lumalyte.lg.infrastructure.placeholders.LumaGuildsExpansion
import net.lumalyte.lg.application.actions.claim.CreateClaim
import net.lumalyte.lg.application.actions.claim.GetClaimAtPosition
import net.lumalyte.lg.application.actions.claim.IsNewClaimLocationValid
import net.lumalyte.lg.application.actions.claim.IsPlayerActionAllowed
import net.lumalyte.lg.application.actions.claim.IsWorldActionAllowed
import net.lumalyte.lg.application.actions.claim.ListPlayerClaims
import net.lumalyte.lg.application.actions.claim.anchor.BreakClaimAnchor
import net.lumalyte.lg.application.actions.claim.anchor.GetClaimAnchorAtPosition
import net.lumalyte.lg.application.actions.claim.anchor.MoveClaimAnchor
import net.lumalyte.lg.application.actions.claim.flag.DisableAllClaimFlags
import net.lumalyte.lg.application.actions.claim.flag.DisableClaimFlag
import net.lumalyte.lg.application.actions.claim.flag.DoesClaimHaveFlag
import net.lumalyte.lg.application.actions.claim.flag.EnableAllClaimFlags
import net.lumalyte.lg.application.actions.claim.flag.EnableClaimFlag
import net.lumalyte.lg.application.actions.claim.flag.GetClaimFlags
import net.lumalyte.lg.application.actions.claim.metadata.GetClaimBlockCount
import net.lumalyte.lg.application.actions.claim.metadata.GetClaimDetails
import net.lumalyte.lg.application.actions.claim.metadata.UpdateClaimDescription
import net.lumalyte.lg.application.actions.claim.metadata.UpdateClaimIcon
import net.lumalyte.lg.application.actions.claim.metadata.UpdateClaimName
import net.lumalyte.lg.application.actions.claim.partition.CanRemovePartition
import net.lumalyte.lg.application.actions.claim.partition.CreatePartition
import net.lumalyte.lg.application.actions.claim.partition.GetClaimPartitions
import net.lumalyte.lg.application.actions.claim.partition.GetPartitionByPosition
import net.lumalyte.lg.application.actions.claim.partition.RemovePartition
import net.lumalyte.lg.application.actions.claim.partition.ResizePartition
import net.lumalyte.lg.application.actions.claim.permission.GetClaimPermissions
import net.lumalyte.lg.application.actions.claim.permission.GetClaimPlayerPermissions
import net.lumalyte.lg.application.actions.claim.permission.GetPlayersWithPermissionInClaim
import net.lumalyte.lg.application.actions.claim.permission.GrantAllClaimWidePermissions
import net.lumalyte.lg.application.actions.claim.permission.GrantAllPlayerClaimPermissions
import net.lumalyte.lg.application.actions.claim.permission.GrantClaimWidePermission
import net.lumalyte.lg.application.actions.claim.permission.GrantGuildMembersClaimPermissions
import net.lumalyte.lg.application.actions.claim.permission.GrantPlayerClaimPermission
import net.lumalyte.lg.application.actions.claim.permission.RevokeAllClaimWidePermissions
import net.lumalyte.lg.application.actions.claim.permission.RevokeAllPlayerClaimPermissions
import net.lumalyte.lg.application.actions.claim.permission.RevokeClaimWidePermission
import net.lumalyte.lg.application.actions.claim.permission.RevokePlayerClaimPermission
import net.lumalyte.lg.application.actions.claim.transfer.AcceptTransferRequest
import net.lumalyte.lg.application.actions.claim.transfer.CanPlayerReceiveTransferRequest
import net.lumalyte.lg.application.actions.claim.transfer.DoesPlayerHaveTransferRequest
import net.lumalyte.lg.application.actions.claim.transfer.OfferPlayerTransferRequest
import net.lumalyte.lg.application.actions.claim.transfer.WithdrawPlayerTransferRequest
import net.lumalyte.lg.application.actions.player.DoesPlayerHaveClaimOverride
import net.lumalyte.lg.application.actions.player.GetRemainingClaimBlockCount
import net.lumalyte.lg.application.actions.player.IsPlayerInClaimMenu
import net.lumalyte.lg.application.actions.player.RegisterClaimMenuOpening
import net.lumalyte.lg.application.actions.player.ToggleClaimOverride
import net.lumalyte.lg.application.actions.player.UnregisterClaimMenuOpening
import net.lumalyte.lg.application.actions.player.tool.GetClaimIdFromMoveTool
import net.lumalyte.lg.application.actions.player.tool.GivePlayerClaimTool
import net.lumalyte.lg.application.actions.player.tool.GivePlayerMoveTool
import net.lumalyte.lg.application.actions.player.tool.IsItemClaimTool
import net.lumalyte.lg.application.actions.player.tool.IsItemMoveTool
import net.lumalyte.lg.application.actions.player.tool.SyncToolVisualization
import net.lumalyte.lg.application.actions.player.visualisation.ClearSelectionVisualisation
import net.lumalyte.lg.application.actions.player.visualisation.ClearVisualisation
import net.lumalyte.lg.application.actions.player.visualisation.DisplaySelectionVisualisation
import net.lumalyte.lg.application.actions.player.visualisation.DisplayVisualisation
import net.lumalyte.lg.application.actions.player.visualisation.GetVisualisedClaimBlocks
import net.lumalyte.lg.application.actions.player.visualisation.GetVisualiserMode
import net.lumalyte.lg.application.actions.player.visualisation.IsPlayerVisualising
import net.lumalyte.lg.application.actions.player.visualisation.RefreshVisualisation
import net.lumalyte.lg.application.actions.player.visualisation.ScheduleClearVisualisation
import net.lumalyte.lg.application.actions.player.visualisation.ToggleVisualiserMode
import net.lumalyte.lg.application.persistence.ClaimFlagRepository
import net.lumalyte.lg.application.persistence.ClaimPermissionRepository
import net.lumalyte.lg.application.persistence.ClaimRepository
import net.lumalyte.lg.application.persistence.PartitionRepository
import net.lumalyte.lg.application.persistence.PlayerAccessRepository
import net.lumalyte.lg.application.persistence.PlayerStateRepository
import net.lumalyte.lg.application.services.ConfigService

import net.lumalyte.lg.application.services.PlayerLocaleService
import net.lumalyte.lg.application.services.BedrockLocalizationService
import net.lumalyte.lg.application.services.FormCacheService
import net.lumalyte.lg.application.services.PlayerMetadataService
import net.lumalyte.lg.application.services.ToolItemService
import net.lumalyte.lg.application.services.VisualisationService
import net.lumalyte.lg.application.services.WorldManipulationService
import net.lumalyte.lg.application.services.scheduling.SchedulerService

import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MapRendererService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RelationService
import net.lumalyte.lg.application.services.GuildRolePermissionResolver
import net.lumalyte.lg.application.services.PartyService
import net.lumalyte.lg.application.services.ChatService
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.persistence.RankRepository
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.persistence.RelationRepository
import net.lumalyte.lg.application.persistence.PartyRepository
import net.lumalyte.lg.application.persistence.GuildInvitationRepository
import net.lumalyte.lg.application.persistence.PartyRequestRepository
import net.lumalyte.lg.application.persistence.PlayerPartyPreferenceRepository
import net.lumalyte.lg.application.persistence.ChatSettingsRepository
import net.lumalyte.lg.application.persistence.BankRepository
import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.application.services.ModeService
import net.lumalyte.lg.application.services.ProgressionService
import net.lumalyte.lg.application.services.GuildBannerService
import net.lumalyte.lg.application.persistence.GuildBannerRepository
import net.lumalyte.lg.application.services.AuditService
import net.lumalyte.lg.application.persistence.AuditRepository
import net.lumalyte.lg.application.services.CombatService
import net.lumalyte.lg.application.services.VisualisationPerformanceService
import net.lumalyte.lg.application.services.KillService
import net.lumalyte.lg.application.services.WarService
import net.lumalyte.lg.application.persistence.KillRepository

import net.lumalyte.lg.infrastructure.services.GuildServiceBukkit
import net.lumalyte.lg.infrastructure.services.RankServiceBukkit
import net.lumalyte.lg.infrastructure.services.MemberServiceBukkit
import net.lumalyte.lg.infrastructure.services.RelationServiceBukkit
import net.lumalyte.lg.infrastructure.services.GuildRolePermissionResolverBukkit
import net.lumalyte.lg.infrastructure.services.NexoEmojiService
import net.lumalyte.lg.infrastructure.services.PartyServiceBukkit
import net.lumalyte.lg.infrastructure.services.ChatServiceBukkit
import net.lumalyte.lg.infrastructure.services.BankServiceBukkit
import net.lumalyte.lg.infrastructure.services.ModeServiceBukkit
import net.lumalyte.lg.infrastructure.services.ProgressionServiceBukkit
import net.lumalyte.lg.infrastructure.services.CombatServiceBukkit
import net.lumalyte.lg.infrastructure.services.GuildBannerServiceBukkit
import net.lumalyte.lg.infrastructure.services.AuditServiceBukkit
import net.lumalyte.lg.infrastructure.services.KillServiceBukkit
import net.lumalyte.lg.infrastructure.services.MapRendererServiceBukkit
import net.lumalyte.lg.infrastructure.services.WarServiceBukkit
import net.lumalyte.lg.infrastructure.services.FloodgatePlatformDetectionService
import net.lumalyte.lg.application.services.PlatformDetectionService
import net.lumalyte.lg.infrastructure.services.BedrockLocalizationServiceFloodgate
import net.lumalyte.lg.infrastructure.services.FormCacheServiceGuava
import net.lumalyte.lg.infrastructure.services.FormValidationServiceImpl
import net.lumalyte.lg.application.services.FormValidationService
import net.lumalyte.lg.interaction.listeners.ChatInputListener
import net.lumalyte.lg.infrastructure.listeners.ProgressionEventListener
import net.lumalyte.lg.infrastructure.persistence.guilds.GuildRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.guilds.RankRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.guilds.MemberRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.guilds.RelationRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.guilds.PartyRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.guilds.GuildInvitationRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.guilds.PartyRequestRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.guilds.PlayerPartyPreferenceRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.guilds.ChatSettingsRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.guilds.BankRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.guilds.ProgressionRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.guilds.GuildBannerRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.AuditRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.guilds.KillRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.claims.ClaimFlagRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.claims.ClaimPermissionRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.claims.ClaimRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.claims.PlayerAccessRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.partitions.PartitionRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.players.PlayerStateRepositoryMemory
import co.aikar.idb.Database
import net.lumalyte.lg.infrastructure.persistence.storage.SQLiteStorage
import net.lumalyte.lg.infrastructure.persistence.storage.MariaDBStorage
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import net.lumalyte.lg.infrastructure.services.ConfigServiceBukkit

import net.lumalyte.lg.infrastructure.services.PlayerLocaleServicePaper
import net.lumalyte.lg.infrastructure.services.PlayerMetadataServiceVault
import net.lumalyte.lg.infrastructure.services.ToolItemServiceBukkit
import net.lumalyte.lg.infrastructure.services.VisualisationServiceBukkit
import net.lumalyte.lg.infrastructure.services.VisualisationPerformanceServiceBukkit
import net.lumalyte.lg.infrastructure.services.WorldManipulationServiceBukkit
import net.lumalyte.lg.infrastructure.services.scheduling.SchedulerServiceBukkit
import net.lumalyte.lg.infrastructure.utilities.LocalizationProviderProperties
import net.lumalyte.lg.application.services.CsvExportService
import net.lumalyte.lg.application.services.FileExportManager
import net.lumalyte.lg.application.services.DiscordCsvService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.milkbowl.vault.chat.Chat
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.plugin.Plugin
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.io.File

// Define your Koin module(s) - using a top-level val named appModule is common
fun appModule(plugin: LumaGuilds, storage: Storage<*>, claimsEnabled: Boolean = true) = module {
    // --- Plugin ---
    single<Plugin> {plugin}
    single<LumaGuilds> {plugin}
    single<File> { plugin.dataFolder }
    single<FileConfiguration> { plugin.config }
    single<Chat> { plugin.metadata }
    single<CoroutineScope> { plugin.pluginScope }
    single<CoroutineDispatcher>(named("IODispatcher")) { Dispatchers.IO }

    // --- Config ---
    single { get<ConfigService>().loadConfig() }


    // --- Infrastructure Layer Implementations ---

    // Storage Types - provided from main class based on config
    // All repositories now accept Storage<Database> and are database-agnostic
    @Suppress("UNCHECKED_CAST")
    single<Storage<Database>> { storage as Storage<Database> }

    // Conditionally load claim-related components
    if (claimsEnabled) {
        // Repositories
        single<ClaimFlagRepository> { ClaimFlagRepositorySQLite(get()) }
        single<ClaimPermissionRepository> { ClaimPermissionRepositorySQLite(get()) }
        single<ClaimRepository> { ClaimRepositorySQLite(get()) }
        single<PartitionRepository> { PartitionRepositorySQLite(get()) }
        single<PlayerAccessRepository> { PlayerAccessRepositorySQLite(get()) }
        single<PlayerStateRepository> { PlayerStateRepositoryMemory() }
    }

    // Services
    single<ConfigService> { ConfigServiceBukkit(get()) }
    single<PlayerLocaleService> { PlayerLocaleServicePaper() }
    single<PlayerMetadataService> { PlayerMetadataServiceVault(get(), get()) }
    // Conditionally load claim-related services
    if (claimsEnabled) {
        single<VisualisationService> { VisualisationServiceBukkit() }
        single<VisualisationPerformanceService> { VisualisationPerformanceServiceBukkit() }
        single<WorldManipulationService> { WorldManipulationServiceBukkit() }
        single<SchedulerService> { SchedulerServiceBukkit(get()) }
        single<ToolItemService> { ToolItemServiceBukkit(get(), get()) }
    }
    

    
    // Guild System
    single<GuildRepository> { GuildRepositorySQLite(get()) }
    single<RankRepository> { RankRepositorySQLite(get()) }
    single<MemberRepository> { MemberRepositorySQLite(get()) }
    single<RelationRepository> { RelationRepositorySQLite(get()) }
    single<PartyRepository> { PartyRepositorySQLite(get()) }
    single<PlayerPartyPreferenceRepository> { PlayerPartyPreferenceRepositorySQLite(get()) }
    single<PartyRequestRepository> { PartyRequestRepositorySQLite(get()) }
    single<ChatSettingsRepository> { ChatSettingsRepositorySQLite(get()) }
    single<BankRepository> { BankRepositorySQLite(get()) }
    single<KillRepository> { KillRepositorySQLite(get()) }
    single<ProgressionRepository> { ProgressionRepositorySQLite(get()) }
    single<GuildInvitationRepository> { GuildInvitationRepositorySQLite(get()) }
    single<net.lumalyte.lg.application.persistence.GuildVaultRepository> { net.lumalyte.lg.infrastructure.persistence.guilds.GuildVaultRepositorySQLite(get()) }
    single<net.lumalyte.lg.infrastructure.persistence.guilds.VaultTransactionLogger> { net.lumalyte.lg.infrastructure.persistence.guilds.VaultTransactionLogger(get()) }

    single<NexoEmojiService> { NexoEmojiService() }
    single<GuildService> { GuildServiceBukkit(get(), get(), get(), get(), get(), get(), get(), get()) }
    single<RankService> { RankServiceBukkit(get(), get(), get(), get()) }
    single<MemberService> { MemberServiceBukkit(get(), get(), get()) }
    single<RelationService> { RelationServiceBukkit(get(), get()) }
    single<PartyService> { PartyServiceBukkit(get(), get(), get(), get()) }
    single<ChatService> { ChatServiceBukkit(get(), get(), get(), get(), get(), get(), get(), get()) }
    single<BankService> { BankServiceBukkit(get(), get(), get(), get()) }
    single<KillService> { KillServiceBukkit(get()) }
    single<WarService> { WarServiceBukkit() }
    single<net.lumalyte.lg.application.services.DailyWarCostsService> {
        net.lumalyte.lg.infrastructure.services.DailyWarCostsServiceBukkit(get(), get(), get())
    }
    single<ModeService> { ModeServiceBukkit(get(), get(), get(), get()) }
    single<CombatService> { CombatServiceBukkit(get()) }
    single<ProgressionService> { ProgressionServiceBukkit(get(), get(), get(), get()) }
    single<GuildRolePermissionResolver> { GuildRolePermissionResolverBukkit(get(), get(), get(), get()) }

    // Teleportation System
    single<net.lumalyte.lg.infrastructure.services.TeleportationService> { net.lumalyte.lg.infrastructure.services.TeleportationService(get()) }

    // Guild Vault System
    single<net.lumalyte.lg.application.services.VaultInventoryManager> {
        val config = get<ConfigService>().loadConfig()
        net.lumalyte.lg.application.services.VaultInventoryManager(get(), get(), config.vault)
    }
    single<net.lumalyte.lg.application.services.GuildVaultService> {
        net.lumalyte.lg.infrastructure.services.GuildVaultServiceBukkit(
            get<LumaGuilds>(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get()
        )
    }
    single<net.lumalyte.lg.application.services.VaultAutoSaveService> {
        val config = get<ConfigService>().loadConfig()
        net.lumalyte.lg.application.services.VaultAutoSaveService(
            get<LumaGuilds>(),
            get(),
            get(),
            config.vault.transactionLogRetentionDays
        )
    }
    single<net.lumalyte.lg.infrastructure.services.VaultHologramService> { net.lumalyte.lg.infrastructure.services.VaultHologramService(get()) }

    // Platform detection service for Bedrock menu system
    single<PlatformDetectionService> { FloodgatePlatformDetectionService(get<LumaGuilds>().logger) }
    
    // Guild Banner System
    single<GuildBannerRepository> { GuildBannerRepositorySQLite(get()) }
    single<GuildBannerService> { GuildBannerServiceBukkit() }
    
    // Audit System
    single<AuditRepository> { AuditRepositorySQLite(get()) }
    single<AuditService> { AuditServiceBukkit() }

    // Utilities
    single<net.lumalyte.lg.application.utilities.LocalizationProvider> { LocalizationProviderProperties(get(), get(), get()) }
    single<BedrockLocalizationService> { BedrockLocalizationServiceFloodgate(get<LumaGuilds>().dataFolder, get(), get()) }
    single<FormCacheService> {
        val config = get<ConfigService>().loadConfig()
        FormCacheServiceGuava(
            maxCacheSize = config.bedrock.formCacheSize,
            cacheExpirationMinutes = config.bedrock.formCacheExpirationMinutes,
            logger = get()
        )
    }
    single<FormValidationService> { FormValidationServiceImpl(get()) }

    // Menu Factory
    single<net.lumalyte.lg.interaction.menus.MenuFactory> { net.lumalyte.lg.interaction.menus.MenuFactory() }

    // Logger (for dependency injection)
    single<java.util.logging.Logger> { get<LumaGuilds>().logger }

    // CSV Export System
    single<CsvExportService> { CsvExportService() }
    single<DiscordCsvService> {
        val config = get<ConfigService>().loadConfig()
        DiscordCsvService(config.discord.webhookUrl)
    }
    single<FileExportManager> {
        FileExportManager(
            pluginDataFolder = get<LumaGuilds>().dataFolder,
            csvExportService = get(),
            discordCsvService = get()
        )
    }

    // Map-based Chart Rendering System
    single<MapRendererService> { MapRendererServiceBukkit() }

    // Listeners
    single<ChatInputListener> { ChatInputListener() }
    single<ProgressionEventListener> { ProgressionEventListener() }
    single<net.lumalyte.lg.interaction.listeners.VaultInventoryListener> {
        val config = get<ConfigService>().loadConfig()
        net.lumalyte.lg.interaction.listeners.VaultInventoryListener(get<LumaGuilds>(), get(), get(), config.vault)
    }

    // --- Application Layer Actions ---

    // Conditionally load claim-related actions
    if (claimsEnabled) {
        // Claim
        singleOf(::CreateClaim)
        single<ConvertClaimToGuild> { ConvertClaimToGuild(get(), get(), get()) }
        singleOf(::GetClaimAtPosition)
        singleOf(::IsNewClaimLocationValid)
        singleOf(::IsPlayerActionAllowed)
        singleOf(::IsWorldActionAllowed)
        singleOf(::ListPlayerClaims)

        // Claim / Anchor
        singleOf(::BreakClaimAnchor)
        singleOf(::GetClaimAnchorAtPosition)
        singleOf(::MoveClaimAnchor)

        // Claim / Flag
        singleOf(::DisableAllClaimFlags)
        singleOf(::DisableClaimFlag)
        singleOf(::DoesClaimHaveFlag)
        singleOf(::EnableAllClaimFlags)
        singleOf(::EnableClaimFlag)
        singleOf(::GetClaimFlags)

        // Claim / Metadata
        singleOf(::GetClaimBlockCount)
        singleOf(::GetClaimDetails)
        singleOf(::UpdateClaimDescription)
        singleOf(::UpdateClaimIcon)
        singleOf(::UpdateClaimName)

        // Claim / Partition
        singleOf(::CanRemovePartition)
        singleOf(::CreatePartition)
        singleOf(::GetClaimPartitions)
        singleOf(::GetPartitionByPosition)
        singleOf(::RemovePartition)
        singleOf(::ResizePartition)

        // Claim / Permission
        singleOf(::GetClaimPermissions)
        singleOf(::GetClaimPlayerPermissions)
        singleOf(::GetPlayersWithPermissionInClaim)
        singleOf(::GrantAllClaimWidePermissions)
        singleOf(::GrantAllPlayerClaimPermissions)
        singleOf(::GrantClaimWidePermission)
        singleOf(::GrantGuildMembersClaimPermissions)
        singleOf(::GrantPlayerClaimPermission)
        singleOf(::RevokeAllClaimWidePermissions)
        singleOf(::RevokeAllPlayerClaimPermissions)
        singleOf(::RevokeClaimWidePermission)
        singleOf(::RevokePlayerClaimPermission)

        // Claim / Transfer
        singleOf(::AcceptTransferRequest)
        singleOf(::CanPlayerReceiveTransferRequest)
        singleOf(::DoesPlayerHaveTransferRequest)
        singleOf(::OfferPlayerTransferRequest)
        singleOf(::WithdrawPlayerTransferRequest)
    }

    // Conditionally load claim-related player actions
    if (claimsEnabled) {
        // Player
        singleOf(::DoesPlayerHaveClaimOverride)
        singleOf(::GetRemainingClaimBlockCount)
        singleOf(::IsPlayerInClaimMenu)
        singleOf(::RegisterClaimMenuOpening)
        singleOf(::UnregisterClaimMenuOpening)
        singleOf(::ToggleClaimOverride)

        // Player / Tool
        singleOf(::GetClaimIdFromMoveTool)
        singleOf(::GivePlayerClaimTool)
        singleOf(::GivePlayerMoveTool)
        singleOf(::IsItemClaimTool)
        singleOf(::IsItemMoveTool)
        singleOf(::SyncToolVisualization)

        // Player / Visualisation
        singleOf(::ClearSelectionVisualisation)
        singleOf(::ClearVisualisation)
        singleOf(::DisplaySelectionVisualisation)
        singleOf(::DisplayVisualisation)
        singleOf(::GetVisualisedClaimBlocks)
        singleOf(::GetVisualiserMode)
        singleOf(::IsPlayerVisualising)
        singleOf(::RefreshVisualisation)
        singleOf(::ScheduleClearVisualisation)
        singleOf(::ToggleVisualiserMode)
    }

    // PlaceholderAPI Expansion
    singleOf(::LumaGuildsExpansion)
}
