package net.lumalyte.lg.di.runtime

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import net.badgersmc.nexus.core.NexusContext
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.persistence.GuildInvitationRepository
import net.lumalyte.lg.application.persistence.GuildShopXpRepository
import net.lumalyte.lg.application.persistence.MembershipHistoryRepository
import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.application.persistence.QuestRepository
import net.lumalyte.lg.application.persistence.RelationRepository
import net.lumalyte.lg.application.persistence.WarRepository
import net.lumalyte.lg.application.services.AdminOverrideService
import net.lumalyte.lg.application.services.BankAutomationService
import net.lumalyte.lg.application.services.BankDepositXpDeliveryService
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.application.services.ChapterTwoGuildAwardService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.DailyWarCostsService
import net.lumalyte.lg.application.services.DiscordAccountLinkSubscription
import net.lumalyte.lg.application.services.DiscordGuildProfileSubscription
import net.lumalyte.lg.application.services.DiscordGuildRoleGateway
import net.lumalyte.lg.application.services.DurableInviteBlockService
import net.lumalyte.lg.application.services.FormCacheService
import net.lumalyte.lg.application.services.GuildCosmeticUnlockService
import net.lumalyte.lg.application.services.GuildDiscordRoleService
import net.lumalyte.lg.application.services.GuildGoldService
import net.lumalyte.lg.application.services.GuildRolePermissionResolver
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.GuildVaultService
import net.lumalyte.lg.application.services.InviteBlockService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.PartyService
import net.lumalyte.lg.application.services.PhysicalCurrencyService
import net.lumalyte.lg.application.services.PlatformDetectionService
import net.lumalyte.lg.application.services.ProgressionService
import net.lumalyte.lg.application.services.QuestService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.application.services.RelationService
import net.lumalyte.lg.application.services.StrikeService
import net.lumalyte.lg.application.services.VaultBackupService
import net.lumalyte.lg.application.services.WarService
import net.lumalyte.lg.application.services.apollo.LunarClientService
import net.lumalyte.lg.config.StrikesConfig
import net.lumalyte.lg.infrastructure.bukkit.bannerman.BannermanListeners
import net.lumalyte.lg.infrastructure.bukkit.bannerman.BannermanRenderService
import net.lumalyte.lg.infrastructure.listeners.ChatDestinationIndicator
import net.lumalyte.lg.infrastructure.listeners.CombatAntiGriefListener
import net.lumalyte.lg.infrastructure.listeners.GuildChannelCreationListener
import net.lumalyte.lg.infrastructure.listeners.GuildChatReconnectListener
import net.lumalyte.lg.infrastructure.listeners.GuildDisbandedListener
import net.lumalyte.lg.infrastructure.listeners.GuildDiscordRoleListener
import net.lumalyte.lg.infrastructure.listeners.GuildEmojiGrantListener
import net.lumalyte.lg.infrastructure.listeners.GuildMuteChatListener
import net.lumalyte.lg.infrastructure.listeners.GuildOnboardingListener
import net.lumalyte.lg.infrastructure.listeners.GuildRecruitmentListener
import net.lumalyte.lg.infrastructure.listeners.InviteBlockChatListener
import net.lumalyte.lg.infrastructure.listeners.PlayerSessionListener
import net.lumalyte.lg.infrastructure.listeners.ProgressionEventListener
import net.lumalyte.lg.infrastructure.listeners.QuestProgressListener
import net.lumalyte.lg.infrastructure.listeners.RoseChatCleanupListener
import net.lumalyte.lg.infrastructure.listeners.SpawnBannerListener
import net.lumalyte.lg.infrastructure.listeners.VaultProtectionListener
import net.lumalyte.lg.infrastructure.listeners.WarBannerListener
import net.lumalyte.lg.infrastructure.listeners.WarKillTrackingListener
import net.lumalyte.lg.infrastructure.listeners.apollo.GuildNotificationListener
import net.lumalyte.lg.infrastructure.listeners.apollo.GuildRichPresenceListener
import net.lumalyte.lg.infrastructure.listeners.apollo.GuildTeamListener
import net.lumalyte.lg.infrastructure.persistence.guilds.RewardOwnershipRepositorySQL
import net.lumalyte.lg.infrastructure.placeholders.LumaGuildsExpansion
import net.lumalyte.lg.di.nexus.nexusLumaGuildsChannelDependencies
import net.lumalyte.lg.infrastructure.services.GuildAchievementRuntime
import net.lumalyte.lg.infrastructure.services.LumaGuildsChannelDependencies
import net.lumalyte.lg.infrastructure.services.GuildEmojiGrantService
import net.lumalyte.lg.infrastructure.services.GuildLeaderboardProjectionService
import net.lumalyte.lg.infrastructure.services.NexoEmojiService
import net.lumalyte.lg.infrastructure.services.SeasonalEloCoordinator
import net.lumalyte.lg.infrastructure.services.SpawnBannerServiceBukkit
import net.lumalyte.lg.infrastructure.services.VaultHologramService
import net.lumalyte.lg.infrastructure.services.WeeklyQuestCoordinator
import net.lumalyte.lg.infrastructure.services.apollo.GuildNotificationService
import net.lumalyte.lg.infrastructure.services.apollo.GuildTeamService
import net.lumalyte.lg.infrastructure.vault.VaultAutoSaveService
import net.lumalyte.lg.infrastructure.web.WebApiServer
import net.lumalyte.lg.interaction.listeners.BannerSelectionListener
import net.lumalyte.lg.interaction.listeners.ChatInputListener
import net.lumalyte.lg.interaction.listeners.ClaimAnchorListener
import net.lumalyte.lg.interaction.listeners.ClaimDestructionListener
import net.lumalyte.lg.interaction.listeners.ClaimPvpProtectionListener
import net.lumalyte.lg.interaction.listeners.CloseInventoryListener
import net.lumalyte.lg.interaction.listeners.EditToolListener
import net.lumalyte.lg.interaction.listeners.EditToolVisualisingListener
import net.lumalyte.lg.interaction.listeners.GuildChatListener
import net.lumalyte.lg.interaction.listeners.HarvestReplantListener
import net.lumalyte.lg.interaction.listeners.MoveToolListener
import net.lumalyte.lg.interaction.listeners.PartitionUpdateListener
import net.lumalyte.lg.interaction.listeners.PartyChatListener
import net.lumalyte.lg.interaction.listeners.PlayerClaimProtectionListener
import net.lumalyte.lg.interaction.listeners.ToolRemovalListener
import net.lumalyte.lg.interaction.listeners.VaultInventoryListener
import net.lumalyte.lg.interaction.listeners.WorldClaimProtectionListener
import kotlin.reflect.KClass

/**
 * Typed external view of the active Nexus graph used by the runtime lifecycle.
 *
 * Each property is one existing singleton; nullable properties are optional integrations that
 * stay unresolved (and unconstructed) when their definition is absent. This replaces the legacy
 * global-container lookups in LumaGuilds.kt without exposing a generic resolver.
 */
internal interface GuildRuntimeGraph {
    val claims: ClaimRuntimeGraph?

    val scope: CoroutineScope
    val virtualDispatcher: CoroutineDispatcher
    val lang: LangService
    val configService: ConfigService
    val strikesConfig: StrikesConfig

    val guildService: GuildService
    val questService: QuestService
    val questRepository: QuestRepository
    val memberService: MemberService
    val rankService: RankService
    val bankService: BankService
    val relationService: RelationService
    val partyService: PartyService
    val progressionService: ProgressionService
    val warService: WarService
    val strikeService: StrikeService
    val guildVaultService: GuildVaultService
    val vaultBackupService: VaultBackupService
    val guildCosmeticUnlockService: GuildCosmeticUnlockService
    val inviteBlockService: InviteBlockService
    val durableInviteBlockService: DurableInviteBlockService
    val dailyWarCostsService: DailyWarCostsService
    val bankAutomationService: BankAutomationService
    val guildGoldService: GuildGoldService
    val bankDepositXpDeliveryService: BankDepositXpDeliveryService
    val chapterTwoGuildAwardService: ChapterTwoGuildAwardService
    val adminOverrideService: AdminOverrideService
    val platformDetectionService: PlatformDetectionService
    val formCacheService: FormCacheService
    val physicalCurrencyService: PhysicalCurrencyService
    val guildRolePermissionResolver: GuildRolePermissionResolver?

    val guildInvitationRepository: GuildInvitationRepository
    val guildShopXpRepository: GuildShopXpRepository
    val membershipHistoryRepository: MembershipHistoryRepository
    val progressionRepository: ProgressionRepository
    val warRepository: WarRepository
    val relationRepository: RelationRepository
    val rewardOwnershipRepository: RewardOwnershipRepositorySQL

    val guildAchievementRuntime: GuildAchievementRuntime
    val seasonalEloCoordinator: SeasonalEloCoordinator
    val nexoEmojiService: NexoEmojiService
    val guildEmojiGrantService: GuildEmojiGrantService
    val weeklyQuestCoordinator: WeeklyQuestCoordinator
    val guildLeaderboardProjectionService: GuildLeaderboardProjectionService
    val spawnBannerService: SpawnBannerServiceBukkit
    val vaultHologramService: VaultHologramService
    val vaultAutoSaveService: VaultAutoSaveService
    val webApiServer: WebApiServer
    val bannermanRenderService: BannermanRenderService
    val bannermanListeners: BannermanListeners

    val discordAccountLinkSubscription: DiscordAccountLinkSubscription
    val discordGuildProfileSubscription: DiscordGuildProfileSubscription
    val discordGuildRoleGateway: DiscordGuildRoleGateway
    val guildDiscordRoleService: GuildDiscordRoleService

    val lumaGuildsExpansion: LumaGuildsExpansion?
    val lunarClientService: LunarClientService?
    val guildTeamService: GuildTeamService?
    val guildTeamListener: GuildTeamListener?
    val guildNotificationService: GuildNotificationService?
    val guildNotificationListener: GuildNotificationListener?
    val guildRichPresenceListener: GuildRichPresenceListener?

    val chatInputListener: ChatInputListener
    val guildChatListener: GuildChatListener
    val partyChatListener: PartyChatListener
    val bannerSelectionListener: BannerSelectionListener
    val warBannerListener: WarBannerListener
    val spawnBannerListener: SpawnBannerListener
    val progressionEventListener: ProgressionEventListener
    val questProgressListener: QuestProgressListener
    val guildChannelCreationListener: GuildChannelCreationListener
    val vaultProtectionListener: VaultProtectionListener
    val vaultInventoryListener: VaultInventoryListener
    val guildOnboardingListener: GuildOnboardingListener
    val guildRecruitmentListener: GuildRecruitmentListener
    val playerSessionListener: PlayerSessionListener
    val warKillTrackingListener: WarKillTrackingListener
    val combatAntiGriefListener: CombatAntiGriefListener
    val guildDisbandedListener: GuildDisbandedListener
    val guildEmojiGrantListener: GuildEmojiGrantListener
    val guildDiscordRoleListener: GuildDiscordRoleListener
    val roseChatCleanupListener: RoseChatCleanupListener
    val guildChatReconnectListener: GuildChatReconnectListener
    val guildMuteChatListener: GuildMuteChatListener
    val inviteBlockChatListener: InviteBlockChatListener
    val chatDestinationIndicator: ChatDestinationIndicator

    /** RoseChat channel collaborators; built per call like the legacy factory, not a bean. */
    fun channelDependencies(): LumaGuildsChannelDependencies
}

/** Claim-only collaborators; absent entirely when claims are disabled. */
internal interface ClaimRuntimeGraph {
    val claimAnchorListener: ClaimAnchorListener
    val claimDestructionListener: ClaimDestructionListener
    val editToolListener: EditToolListener
    val editToolVisualisingListener: EditToolVisualisingListener
    val harvestReplantListener: HarvestReplantListener
    val moveToolListener: MoveToolListener
    val partitionUpdateListener: PartitionUpdateListener
    val playerClaimProtectionListener: PlayerClaimProtectionListener
    val claimPvpProtectionListener: ClaimPvpProtectionListener
    val toolRemovalListener: ToolRemovalListener
    val worldClaimProtectionListener: WorldClaimProtectionListener
    val closeInventoryListener: CloseInventoryListener
}

/** Resolves [GuildRuntimeGraph] from the single active [NexusContext]; lookups are lazy per use. */
internal class NexusGuildRuntimeGraph(
    private val context: NexusContext,
    claimsEnabled: Boolean,
) : GuildRuntimeGraph {
    private inline fun <reified T : Any> bean(): T = context.getBean(T::class)

    /** Optional beans are registered under their decapitalized simple name; absent means not installed. */
    private inline fun <reified T : Any> optional(): T? = optionalBean(T::class)

    private fun <T : Any> optionalBean(type: KClass<T>): T? {
        val name = requireNotNull(type.simpleName).replaceFirstChar { it.lowercase() }
        @Suppress("UNCHECKED_CAST")
        return if (context.containsBean(name)) context.getBean(name) as T else null
    }

    override val claims: ClaimRuntimeGraph? = if (claimsEnabled) NexusClaimRuntimeGraph(context) else null

    override val scope: CoroutineScope get() = requireNotNull(context.scope) { "Nexus context has no coroutine scope" }
    override val virtualDispatcher: CoroutineDispatcher
        get() = requireNotNull(context.dispatchers) { "Nexus context has no dispatchers" }.virtualThread
    override val lang: LangService get() = bean()
    override val configService: ConfigService get() = bean()
    override val strikesConfig: StrikesConfig get() = bean()

    override val guildService: GuildService get() = bean()
    override val questService: QuestService get() = bean()
    override val questRepository: QuestRepository get() = bean()
    override val memberService: MemberService get() = bean()
    override val rankService: RankService get() = bean()
    override val bankService: BankService get() = bean()
    override val relationService: RelationService get() = bean()
    override val partyService: PartyService get() = bean()
    override val progressionService: ProgressionService get() = bean()
    override val warService: WarService get() = bean()
    override val strikeService: StrikeService get() = bean()
    override val guildVaultService: GuildVaultService get() = bean()
    override val vaultBackupService: VaultBackupService get() = bean()
    override val guildCosmeticUnlockService: GuildCosmeticUnlockService get() = bean()
    override val inviteBlockService: InviteBlockService get() = bean()
    override val durableInviteBlockService: DurableInviteBlockService get() = bean()
    override val dailyWarCostsService: DailyWarCostsService get() = bean()
    override val bankAutomationService: BankAutomationService get() = bean()
    override val guildGoldService: GuildGoldService get() = bean()
    override val bankDepositXpDeliveryService: BankDepositXpDeliveryService get() = bean()
    override val chapterTwoGuildAwardService: ChapterTwoGuildAwardService get() = bean()
    override val adminOverrideService: AdminOverrideService get() = bean()
    override val platformDetectionService: PlatformDetectionService get() = bean()
    override val formCacheService: FormCacheService get() = bean()
    override val physicalCurrencyService: PhysicalCurrencyService get() = bean()
    override val guildRolePermissionResolver: GuildRolePermissionResolver? get() = optional()

    override val guildInvitationRepository: GuildInvitationRepository get() = bean()
    override val guildShopXpRepository: GuildShopXpRepository get() = bean()
    override val membershipHistoryRepository: MembershipHistoryRepository get() = bean()
    override val progressionRepository: ProgressionRepository get() = bean()
    override val warRepository: WarRepository get() = bean()
    override val relationRepository: RelationRepository get() = bean()
    override val rewardOwnershipRepository: RewardOwnershipRepositorySQL get() = bean()

    override val guildAchievementRuntime: GuildAchievementRuntime get() = bean()
    override val seasonalEloCoordinator: SeasonalEloCoordinator get() = bean()
    override val nexoEmojiService: NexoEmojiService get() = bean()
    override val guildEmojiGrantService: GuildEmojiGrantService get() = bean()
    override val weeklyQuestCoordinator: WeeklyQuestCoordinator get() = bean()
    override val guildLeaderboardProjectionService: GuildLeaderboardProjectionService get() = bean()
    override val spawnBannerService: SpawnBannerServiceBukkit get() = bean()
    override val vaultHologramService: VaultHologramService get() = bean()
    override val vaultAutoSaveService: VaultAutoSaveService get() = bean()
    override val webApiServer: WebApiServer get() = bean()
    override val bannermanRenderService: BannermanRenderService get() = bean()
    override val bannermanListeners: BannermanListeners get() = bean()

    override val discordAccountLinkSubscription: DiscordAccountLinkSubscription get() = bean()
    override val discordGuildProfileSubscription: DiscordGuildProfileSubscription get() = bean()
    override val discordGuildRoleGateway: DiscordGuildRoleGateway get() = bean()
    override val guildDiscordRoleService: GuildDiscordRoleService get() = bean()

    override val lumaGuildsExpansion: LumaGuildsExpansion? get() = optional()
    override val lunarClientService: LunarClientService? get() = optional()
    override val guildTeamService: GuildTeamService? get() = optional()
    override val guildTeamListener: GuildTeamListener? get() = optional()
    override val guildNotificationService: GuildNotificationService? get() = optional()
    override val guildNotificationListener: GuildNotificationListener? get() = optional()
    override val guildRichPresenceListener: GuildRichPresenceListener? get() = optional()

    override val chatInputListener: ChatInputListener get() = bean()
    override val guildChatListener: GuildChatListener get() = bean()
    override val partyChatListener: PartyChatListener get() = bean()
    override val bannerSelectionListener: BannerSelectionListener get() = bean()
    override val warBannerListener: WarBannerListener get() = bean()
    override val spawnBannerListener: SpawnBannerListener get() = bean()
    override val progressionEventListener: ProgressionEventListener get() = bean()
    override val questProgressListener: QuestProgressListener get() = bean()
    override val guildChannelCreationListener: GuildChannelCreationListener get() = bean()
    override val vaultProtectionListener: VaultProtectionListener get() = bean()
    override val vaultInventoryListener: VaultInventoryListener get() = bean()
    override val guildOnboardingListener: GuildOnboardingListener get() = bean()
    override val guildRecruitmentListener: GuildRecruitmentListener get() = bean()
    override val playerSessionListener: PlayerSessionListener get() = bean()
    override val warKillTrackingListener: WarKillTrackingListener get() = bean()
    override val combatAntiGriefListener: CombatAntiGriefListener get() = bean()
    override val guildDisbandedListener: GuildDisbandedListener get() = bean()
    override val guildEmojiGrantListener: GuildEmojiGrantListener get() = bean()
    override val guildDiscordRoleListener: GuildDiscordRoleListener get() = bean()
    override val roseChatCleanupListener: RoseChatCleanupListener get() = bean()
    override val guildChatReconnectListener: GuildChatReconnectListener get() = bean()
    override val guildMuteChatListener: GuildMuteChatListener get() = bean()
    override val inviteBlockChatListener: InviteBlockChatListener get() = bean()
    override val chatDestinationIndicator: ChatDestinationIndicator get() = bean()

    override fun channelDependencies(): LumaGuildsChannelDependencies = context.nexusLumaGuildsChannelDependencies()
}

private class NexusClaimRuntimeGraph(private val context: NexusContext) : ClaimRuntimeGraph {
    private inline fun <reified T : Any> bean(): T = context.getBean(T::class)

    override val claimAnchorListener: ClaimAnchorListener get() = bean()
    override val claimDestructionListener: ClaimDestructionListener get() = bean()
    override val editToolListener: EditToolListener get() = bean()
    override val editToolVisualisingListener: EditToolVisualisingListener get() = bean()
    override val harvestReplantListener: HarvestReplantListener get() = bean()
    override val moveToolListener: MoveToolListener get() = bean()
    override val partitionUpdateListener: PartitionUpdateListener get() = bean()
    override val playerClaimProtectionListener: PlayerClaimProtectionListener get() = bean()
    override val claimPvpProtectionListener: ClaimPvpProtectionListener get() = bean()
    override val toolRemovalListener: ToolRemovalListener get() = bean()
    override val worldClaimProtectionListener: WorldClaimProtectionListener get() = bean()
    override val closeInventoryListener: CloseInventoryListener get() = bean()
}
